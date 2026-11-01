#!/usr/bin/env python3
"""Build an isolated network-disabled AF WebView fixture probe, never with the release key."""
import argparse,pathlib,shutil,subprocess,tempfile,hashlib,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--sdk',required=True);p.add_argument('--out',required=True);a=p.parse_args()
here=pathlib.Path(__file__).resolve().parent;out=pathlib.Path(a.out).resolve();out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='tong-af-device-source-') as tmp:
 root=pathlib.Path(tmp)/'source';shutil.copytree(pathlib.Path(a.source).resolve(),root,ignore=shutil.ignore_patterns('target','node_modules','.gradle','build','.git','__pycache__'))
 shutil.copy2(here/'src/kr/tong/sky/LabInstrumentation.java',root/'tong-sky/src/debug/java/kr/tong/sky/LabInstrumentation.java')
 inputs=sorted((root/'tong-sky/src/main/java/kr/tong/sky').glob('AirFrance*.java'))+[root/'tong-sky/src/main/assets/af-capture.js',root/'tong-sky/src/main/assets/af-auto.js',root/'tong-sky/src/main/AndroidManifest.xml']
 (out/'input-af-sha256.json').write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs},indent=2)+'\n')
 manifest=root/'tong-sky/src/main/AndroidManifest.xml';tree=ET.parse(manifest);ns='{http://schemas.android.com/apk/res/android}'
 for node in list(tree.getroot()):
  if node.tag=='uses-permission' and node.get(ns+'name')=='android.permission.INTERNET':tree.getroot().remove(node)
 tree.write(manifest,encoding='utf-8',xml_declaration=True)
 key=pathlib.Path(tmp)/'debug.keystore';password=pathlib.Path(tmp)/'password.txt';password.write_text('android\n')
 subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(key),'--storepass','android','--keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Offline AF Test,O=Test,C=KR'],check=True,capture_output=True)
 subprocess.run(['python3',str(root/'tong-sky/scripts/build-local.py'),'--sdk',str(pathlib.Path(a.sdk).resolve()),'--out',str(out),'--keystore',str(key),'--password-file',str(password),'--debug'],check=True)
 apks=[p for p in out.glob('TongSKY-*.apk') if p.name!='TongSKY-AF-auto-offline-probe.apk'];assert len(apks)==1,apks;shutil.copy2(apks[0],out/'TongSKY-AF-auto-offline-probe.apk')
print(out/'TongSKY-AF-auto-offline-probe.apk')
