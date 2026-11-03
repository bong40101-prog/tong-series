from pathlib import Path
import subprocess,zipfile,tempfile
root=Path(__file__).resolve().parents[1];sdk=Path('/workspace/scratch/06a46cae3e50/android-sdk');tools=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar';p=root/'probe';out=p/'build';out.mkdir(exist_ok=True)
def run(*args):subprocess.run(list(map(str,args)),check=True)
(out/'classes').mkdir(exist_ok=True);(out/'dex').mkdir(exist_ok=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="kr.tong.sky.mobileqa"><application android:label="TongSKY offline mobile QA" android:debuggable="true"><activity android:name=".ProbeActivity" android:exported="true" android:theme="@android:style/Theme.Material.Light.NoActionBar"/></application><instrumentation android:name=".Probe" android:targetPackage="kr.tong.sky.mobileqa"/></manifest>''')
run('java','com.sun.tools.javac.Main','-source','17','-target','17','-cp',android,'-d',out/'classes',*sorted((p/'src').glob('*.java')))
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for f in (out/'classes').rglob('*.class'):z.write(f,f.relative_to(out/'classes'))
run(tools/'d8','--release','--min-api','33','--lib',android,'--output',out/'dex',out/'classes.jar')
run(tools/'aapt2','link','-I',android,'--manifest',out/'AndroidManifest.xml','--min-sdk-version','33','--target-sdk-version','35','--version-code','1','--version-name','1','-A',p/'assets','-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
 for f in (out/'dex').glob('*.dex'):z.write(f,f.name)
run(tools/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
with tempfile.TemporaryDirectory() as tmp:
 key=Path(tmp)/'test.jks';subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(key),'-storepass','android','-keypass','android','-alias','test','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Offline QA'],check=True,capture_output=True)
 run(tools/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--out',root/'mobile-probe.apk',out/'aligned.apk')
