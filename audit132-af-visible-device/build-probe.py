#!/usr/bin/env python3
"""Build explicit functional/process offline variants, never with the release key."""
import argparse,pathlib,shutil,subprocess,tempfile,hashlib,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--sdk',required=True);p.add_argument('--out',required=True);a=p.parse_args()
here=pathlib.Path(__file__).resolve().parent;out=pathlib.Path(a.out).resolve();out.mkdir(parents=True,exist_ok=True)
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
with tempfile.TemporaryDirectory(prefix='tong-af132-source-') as tmp:
 root=pathlib.Path(tmp)/'source';shutil.copytree(pathlib.Path(a.source).resolve(),root,ignore=shutil.ignore_patterns('target','node_modules','.gradle','build','.git','__pycache__','*.apk'))
 for source in (here/'src/kr/tong/sky').glob('*.java'):shutil.copy2(source,root/'tong-sky/src/debug/java/kr/tong/sky'/source.name)
 main=root/'tong-sky/src/main';original=(main/'AndroidManifest.xml').read_bytes();ns='{http://schemas.android.com/apk/res/android}';ET.register_namespace('android',ns[1:-1])
 assets=[main/'assets'/name for name in ('af-capture.js','af-auto.js','af-visible.js')]
 af_inputs=sorted((main/'java/kr/tong/sky').glob('AirFrance*.java'))+assets+[main/'AndroidManifest.xml']
 all_inputs=sorted((main/'java').rglob('*.java'))+assets+[main/'AndroidManifest.xml',root/'tong-sky/scripts/build-local.py',root/'tong-sky/libraries/dependencies.json']
 for name,inputs in [('input-af-sha256.json',af_inputs),('input-source-sha256.json',all_inputs)]:
  (out/name).write_text(json.dumps({str(path.relative_to(root)):sha(path) for path in inputs},indent=2)+'\n')
 key=pathlib.Path(tmp)/'debug.keystore';password=pathlib.Path(tmp)/'password.txt';password.write_text('android\n')
 subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(key),'--storepass','android','--keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Offline AF132 Test,O=Test,C=KR'],check=True,capture_output=True)
 evidence={'schema':1,'networkPermission':False,'disposableSharedDebugKey':True,'variants':{}}
 for mode in ('functional','process'):
  tree=ET.ElementTree(ET.fromstring(original));doc=tree.getroot();app=doc.find('application');af=next(n for n in app.findall('activity') if n.get(ns+'name') in ('.AirFranceActivity','kr.tong.sky.AirFranceActivity'))
  production_process=af.get(ns+'process');assert production_process==':airfrance','Freeze must include production AF child process'
  for node in list(doc):
   if node.tag=='uses-permission' and node.get(ns+'name')=='android.permission.INTERNET':doc.remove(node)
  if mode=='functional':af.set(ns+'process','kr.tong.sky')
  else:
   at=list(app).index(af);attributes=dict(af.attrib);attributes[ns+'name']='kr.tong.sky.RemoteAFProbeActivity';attributes[ns+'process']=production_process;attributes[ns+'exported']='false';app.remove(af)
   app.insert(at,ET.Element('activity',attributes));app.insert(at+1,ET.Element('activity-alias',{ns+'name':'kr.tong.sky.AirFranceActivity',ns+'targetActivity':'kr.tong.sky.RemoteAFProbeActivity',ns+'exported':'false'}))
  tree.write(main/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
  build=out/mode
  subprocess.run(['python3',str(root/'tong-sky/scripts/build-local.py'),'--sdk',str(pathlib.Path(a.sdk).resolve()),'--out',str(build),'--keystore',str(key),'--password-file',str(password),'--debug'],check=True)
  apks=list(build.glob('TongSKY-*.apk'));assert len(apks)==1,apks
  target=out/('TongSKY-AF-visible-'+mode+'-probe.apk');shutil.copy2(apks[0],target)
  final_manifest=build/'tong-sky/AndroidManifest.xml';shutil.copy2(final_manifest,out/(mode+'-AndroidManifest.xml'))
  evidence['variants'][mode]={'apk':target.name,'sha256':sha(target),'bytes':target.stat().st_size,'productionAFProcess':production_process,'effectiveAFProcess':'kr.tong.sky' if mode=='functional' else production_process,'debugMainProcessOverride':mode=='functional','debugActivityAlias':None if mode=='functional' else 'kr.tong.sky.AirFranceActivity -> kr.tong.sky.RemoteAFProbeActivity','claimsProductionProcessIsolation':mode=='process','effectiveManifestSha256':sha(final_manifest)}
 (out/'probe-build-manifest.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps(evidence,indent=2))
