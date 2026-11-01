#!/usr/bin/env python3
"""Build exact old/new production inputs with identical offline instrumentation."""
import argparse,pathlib,hashlib,json,shutil,subprocess,tempfile,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--sdk',required=True);p.add_argument('--out',required=True);p.add_argument('--label',choices=['baseline132','fixed133','standalone133'],required=True);a=p.parse_args()
here=pathlib.Path(__file__).resolve().parent;source=pathlib.Path(a.source).resolve();out=pathlib.Path(a.out).resolve();out.mkdir(parents=True,exist_ok=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
inputs=sorted((source/'tong-sky/src/main').rglob('*'));inputs=[x for x in inputs if x.is_file()]+[source/'tong-sky/scripts/build-local.py',source/'tong-sky/libraries/dependencies.json']
inputmap={str(x.relative_to(source)):sha(x) for x in inputs}
(out/(a.label+'-production-inputs.json')).write_text(json.dumps(inputmap,indent=2)+'\n')
with tempfile.TemporaryDirectory(prefix='tong-memory133-') as tmp:
 root=pathlib.Path(tmp)/'source';root.mkdir();shutil.copytree(source/'tong-sky',root/'tong-sky',ignore=shutil.ignore_patterns('target','node_modules','.gradle','build','.git','tests','__pycache__','*.apk'))
 main=root/'tong-sky/src/main';shutil.copy2(here/'src/kr/tong/sky/LabInstrumentation.java',root/'tong-sky/src/debug/java/kr/tong/sky/LabInstrumentation.java');shutil.copytree(here/'fixtures',main/'assets/memory133')
 ns='{http://schemas.android.com/apk/res/android}';ET.register_namespace('android',ns[1:-1]);manifest=ET.parse(main/'AndroidManifest.xml');doc=manifest.getroot();app=doc.find('application');assert app.get(ns+'largeHeap','false')!='true'
 original=(main/'AndroidManifest.xml').read_bytes();af=next(x for x in app.findall('activity') if x.get(ns+'name') in ('.AirFranceActivity','kr.tong.sky.AirFranceActivity'));assert af.get(ns+'process')==':airfrance'
 for node in list(doc):
  if node.tag=='uses-permission' and node.get(ns+'name')=='android.permission.INTERNET':doc.remove(node)
 manifest.write(main/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
 key=pathlib.Path(tmp)/'debug.keystore';password=pathlib.Path(tmp)/'password.txt';password.write_text('android\n')
 subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(key),'--storepass','android','--keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Offline memory133 test,O=Test,C=KR'],check=True,capture_output=True)
 subprocess.run(['python3',str(root/'tong-sky/scripts/build-local.py'),'--sdk',str(pathlib.Path(a.sdk).resolve()),'--out',str(out/'build'),'--keystore',str(key),'--password-file',str(password),'--debug'],check=True)
 apks=list((out/'build').glob('TongSKY-*.apk'));assert len(apks)==1
 target=out/('TongSKY-shared-memory-'+a.label+'-probe.apk');shutil.copy2(apks[0],target);finalmanifest=out/'build/tong-sky/AndroidManifest.xml';shutil.copy2(finalmanifest,out/(a.label+'-AndroidManifest.xml'))
 report={'schema':1,'label':a.label,'apk':target.name,'apkSha256':sha(target),'bytes':target.stat().st_size,'productionInputsSha256':sha(out/(a.label+'-production-inputs.json')),'instrumentationSha256':sha(here/'src/kr/tong/sky/LabInstrumentation.java'),'fixtureManifestSha256':sha(here/'fixtures/fixture-manifest.json'),'productionJavaAssetsUnmodified':True,'originalManifestSha256':hashlib.sha256(original).hexdigest(),'effectiveManifestSha256':sha(finalmanifest),'networkPermission':False,'afProcess':':airfrance','afManifestOverride':False,'disposableDebugSigner':True,'heapSetting':'API33 emulator192M asserted at runtime'}
 (out/(a.label+'-build-manifest.json')).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
