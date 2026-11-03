from pathlib import Path
import subprocess,zipfile,base64,hashlib,json
r=Path(__file__).resolve().parent;out=r/'build';out.mkdir(exist_ok=True);(out/'classes').mkdir(exist_ok=True);(out/'dex').mkdir(exist_ok=True)
sdk=Path('/workspace/scratch/06a46cae3e50/android-sdk');t=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
def run(*args):subprocess.run(list(map(str,args)),check=True)
(out/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="kr.tong.sky.backgroundqa"><application android:label="TongSKY background QA" android:debuggable="true"/><instrumentation android:name=".BackgroundProbe" android:targetPackage="kr.tong.sky"/></manifest>')
run('java','com.sun.tools.javac.Main','-source','17','-target','17','-cp',android,'-d',out/'classes',r/'src/BackgroundProbe.java')
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for p in (out/'classes').rglob('*.class'):z.write(p,p.relative_to(out/'classes'))
run(t/'d8','--release','--min-api','33','--lib',android,'--output',out/'dex',out/'classes.jar')
run(t/'aapt2','link','-I',android,'--manifest',out/'AndroidManifest.xml','--min-sdk-version','33','--target-sdk-version','35','-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
 for p in (out/'dex').glob('*.dex'):z.write(p,p.name)
run(t/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(t/'apksigner','sign','--ks',r.parent/'private-signing/TongSky.keystore','--ks-pass','file:'+str(r.parent/'private-signing/password.txt'),'--out',r/'probe.apk',out/'aligned.apk')
run('zstd','-q','-f','-19','--long=27','--patch-from='+str(r.parent/'build1042/TongSKY-1.0.42.apk'),r.parent/'build1042-debug/TongSKY-1.0.42.apk','-o',r/'debug.zst')
(r/'debug.zst.b64').write_text(base64.b64encode((r/'debug.zst').read_bytes()).decode());(r/'probe.apk.b64').write_text(base64.b64encode((r/'probe.apk').read_bytes()).decode())
hash=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(r/'hashes.json').write_text(json.dumps({'release':hash(r.parent/'build1042/TongSKY-1.0.42.apk'),'debug':hash(r.parent/'build1042-debug/TongSKY-1.0.42.apk'),'probe':hash(r/'probe.apk')},indent=2))
