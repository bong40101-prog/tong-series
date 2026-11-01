#!/usr/bin/env python3
"""Build an isolated offline debug probe; never use the production signing key."""
import argparse,pathlib,shutil,subprocess,tempfile,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--sdk',required=True);p.add_argument('--out',required=True);p.add_argument('--cache-fixture',required=True,help='Production gzip-base64 JSON envelope from a public Asiana shared cache; never credentials. At least 1000 records.');a=p.parse_args()
here=pathlib.Path(__file__).resolve().parent;out=pathlib.Path(a.out).resolve();out.mkdir(parents=True,exist_ok=True)
cache=pathlib.Path(a.cache_fixture).resolve()
if not cache.is_file():p.error('--cache-fixture must name an existing public-cache JSON envelope')
envelope=json.loads(cache.read_text())
if set(envelope)!={'encoding','data'} or envelope.get('encoding')!='gzip-base64' or not isinstance(envelope.get('data'),str):p.error('--cache-fixture must preserve the production gzip-base64 envelope')

with tempfile.TemporaryDirectory(prefix='tong-device-source-') as tmp:
 root=pathlib.Path(tmp)/'source';shutil.copytree(pathlib.Path(a.source).resolve(),root,ignore=shutil.ignore_patterns('target','node_modules','.gradle','build','.git','__pycache__'))
 dst=root/'tong-sky/src/debug/java/kr/tong/sky/LabInstrumentation.java';shutil.copy2(here/'src/kr/tong/sky/LabInstrumentation.java',dst)
 # Asset is added only inside this disposable debug copy, never to release source.
 shutil.copy2(cache,root/'tong-sky/src/main/assets/device-cache-fixture.json')
 manifest=root/'tong-sky/src/main/AndroidManifest.xml';tree=ET.parse(manifest);ns='{http://schemas.android.com/apk/res/android}'
 for node in list(tree.getroot()):
  if node.tag=='uses-permission' and node.get(ns+'name')=='android.permission.INTERNET':tree.getroot().remove(node)
 tree.write(manifest,encoding='utf-8',xml_declaration=True)
 # This is a disposable fixture key, unrelated to the user's release identity.
 key=pathlib.Path(tmp)/'debug.keystore';password=pathlib.Path(tmp)/'password.txt';password.write_text('android\n')
 subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(key),'--storepass','android','--keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Offline Test,O=Test,C=KR'],check=True,capture_output=True)
 subprocess.run(['python3',str(root/'tong-sky/scripts/build-local.py'),'--sdk',str(pathlib.Path(a.sdk).resolve()),'--out',str(out),'--keystore',str(key),'--password-file',str(password),'--debug'],check=True)
 apks=[p for p in out.glob('TongSKY-*.apk') if p.name!='TongSKY-offline-probe.apk'];assert len(apks)==1,apks;shutil.copy2(apks[0],out/'TongSKY-offline-probe.apk')
print(out/'TongSKY-offline-probe.apk')
