#!/usr/bin/env python3
"""Build the two plain-Java Android modules using installed SDK tools, without Gradle.
Usage: python build-local.py --sdk PATH --out PATH --keystore PATH --password-file PATH
Does not run device tests; never presents a successful build as live-query verification.
"""
import argparse, pathlib, subprocess, shutil, zipfile, xml.etree.ElementTree as ET, json, hashlib, os
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--out',required=True);p.add_argument('--keystore',required=True);p.add_argument('--password-file',required=True);p.add_argument('--debug',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[2];sdk=pathlib.Path(a.sdk);out=pathlib.Path(a.out).resolve();out.mkdir(parents=True,exist_ok=True)
tools=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar';main=root/'tong-sky/src/main'
def run(*args):subprocess.run([str(x) for x in args],check=True)
for module,package,version,code,scanner,name in [('tong-sky','kr.tong.sky','1.0.42',10146,True,'TongSKY')]:
 work=out/module;shutil.rmtree(work,ignore_errors=True);work.mkdir(exist_ok=True);generated=work/'generated';generated.mkdir(exist_ok=True);classes=work/'classes';classes.mkdir(exist_ok=True);dex=work/'dex';dex.mkdir(exist_ok=True)
 # Pinned AndroidX runtime, including its generated resource namespaces. No downloads during a build.
 dep_root=root/module/'libraries';dependencies=json.loads((dep_root/'dependencies.json').read_text());dependency_jars=[];library_resources=[];packages=[]
 for dep in dependencies:
  assert pathlib.Path(dep['path']).name==dep['path']
  artifact=dep_root/dep['path'];assert hashlib.sha256(artifact.read_bytes()).hexdigest()==dep['sha256'],artifact
  if dep['format']=='aar':
   library=work/'libraries'/artifact.stem;library.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(artifact) as z:
    content=z.read('classes.jar');assert hashlib.sha256(content).hexdigest()==dep['classesSha256'];jar_path=library/'classes.jar';jar_path.write_bytes(content);dependency_jars.append(jar_path)
    package_name=ET.fromstring(z.read('AndroidManifest.xml')).get('package');packages.append(package_name)
    for member in z.infolist():
     if member.filename.startswith('res/') and not member.is_dir():
      assert '..' not in pathlib.PurePosixPath(member.filename).parts
      dest=library/member.filename;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(member))
   if (library/'res').exists():
    compiled=library/'resources.zip';run(tools/'aapt2','compile','--dir',library/'res','-o',compiled);library_resources.append(compiled)
  else:dependency_jars.append(artifact)
 ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns);manifest=ET.parse(root/module/'src/main/AndroidManifest.xml');mr=manifest.getroot();mr.set('package',package)
 for node in mr.iter():
  key='{'+ns+'}name';value=node.get(key,'')
  if value.startswith('.'):node.set(key,'kr.tong.sky'+value)
 if a.debug:
  app=mr.find('application');app.set('{'+ns+'}debuggable','true');ET.SubElement(app,'activity',{'{'+ns+'}name':'kr.tong.sky.LabActivity','{'+ns+'}exported':'true'});ET.SubElement(mr,'instrumentation',{'{'+ns+'}name':'kr.tong.sky.LabInstrumentation','{'+ns+'}targetPackage':package})
 path=work/'AndroidManifest.xml';manifest.write(path,encoding='utf-8',xml_declaration=True)
 res=work/'resources.zip';run(tools/'aapt2','compile','--dir',main/'res','-o',res)
 unsigned=work/'unsigned.apk';link=[tools/'aapt2','link','-I',android,'--manifest',path,'--min-sdk-version','33','--target-sdk-version','35','--version-code',code,'--version-name',version,'--custom-package','kr.tong.sky','--java',generated,'-A',main/'assets','--auto-add-overlay','--extra-packages',':'.join(packages)]
 for compiled in library_resources+[res]:link.extend(['-R',compiled])
 run(*link,'-o',unsigned)
 config=generated/'kr/tong/sky/BuildConfig.java';config.parent.mkdir(parents=True,exist_ok=True);config.write_text('package kr.tong.sky; public final class BuildConfig {public static final boolean DEBUG='+str(a.debug).lower()+', SCANNER='+str(scanner).lower()+'; public static final String APPLICATION_ID="'+package+'", VERSION_NAME="'+version+'";public static final int VERSION_CODE='+str(code)+';}')
 sources=sorted((main/'java').rglob('*.java'))+sorted(generated.rglob('*.java'));sources+=sorted((root/module/'src/debug/java').rglob('*.java')) if a.debug else [];run('java','com.sun.tools.javac.Main','-source','17','-target','17','-cp',os.pathsep.join(str(p) for p in [android]+dependency_jars),'-d',classes,*sources)
 jar=work/'classes.jar'
 with zipfile.ZipFile(jar,'w') as z:
  for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes))
  # The same complete classpath is used by D8 and JVM integration tests.
  included={str(file.relative_to(classes)).replace(os.sep,'/'):hashlib.sha256(file.read_bytes()).hexdigest() for file in classes.rglob('*.class')}
  for dependency in dependency_jars:
   with zipfile.ZipFile(dependency) as library:
    for member in library.namelist():
     if member.endswith('.class') and not member.startswith('META-INF/') and member!='module-info.class':
      content=library.read(member)
      if member in included:
       assert included[member]==hashlib.sha256(content).hexdigest(), 'Conflicting runtime class: '+member
      else:z.writestr(member,content);included[member]=hashlib.sha256(content).hexdigest()
 run(tools/'d8','--release','--min-api','33','--lib',android,'--output',dex,jar)
 with zipfile.ZipFile(unsigned,'a') as z:
  for file in dex.glob('*.dex'):z.write(file,file.name)
 aligned=work/'aligned.apk';run(tools/'zipalign','-f','4',unsigned,aligned)
 apk=out/(name+'-'+version+'.apk');run(tools/'apksigner','sign','--ks',pathlib.Path(a.keystore).resolve(),'--ks-pass','file:'+str(pathlib.Path(a.password_file).resolve()),'--out',apk,aligned);run(tools/'apksigner','verify','--verbose',apk)
 print('Built',apk)
