import subprocess,json,time,re,hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

out=Path('evidence');out.mkdir(exist_ok=True)
def run(*args):
    result=subprocess.run(list(map(str,args)),text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if result.returncode:print(result.stdout,flush=True);result.check_returncode()
    return result.stdout
def adb(*args):return run('adb','-s','emulator-5554',*args)
def shell(*args):return adb('shell',*args)
def dump():
    # WebView accessibility can be unavailable during startup. Never reuse
    # a previous activity's dump when uiautomator reports a missing root.
    for attempt in range(10):
        shell('rm','-f','/sdcard/guide.xml')
        result=shell('uiautomator','dump','/sdcard/guide.xml')
        if 'UI hierchary dumped to' in result or 'UI hierarchy dumped to' in result:
            try:return ET.fromstring(adb('exec-out','cat','/sdcard/guide.xml'))
            except ET.ParseError:pass
        print('Waiting for current accessibility tree:',result.strip(),flush=True)
        time.sleep(1)
    with (out/'missing-root.png').open('wb') as f:subprocess.run(['adb','-s','emulator-5554','exec-out','screencap','-p'],stdout=f,check=True)
    raise AssertionError('Current accessibility tree unavailable')
def node(label):
    for el in dump().iter('node'):
        if label in (el.get('text'),el.get('content-desc')):return el
    return None
def wait(label):
    for i in range(8):
        el=node(label)
        if el is not None:return el
        time.sleep(1)
    capture('failure-'+re.sub(r'[^a-zA-Z0-9]','_',label));raise AssertionError('Missing UI: '+label)
def tap(label):
    el=wait(label);x1,y1,x2,y2=map(int,re.findall(r'\d+',el.get('bounds')))
    assert x2>x1 and y2>y1,(label,el.attrib)
    shell('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7)
def capture(name):
    (out/(name+'.xml')).write_text(ET.tostring(dump(),encoding='unicode'))
    with (out/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
def seed(package,name,content):
    p=out/(package+'-'+name);p.write_text(content)
    dest='/data/user/0/'+package+'/shared_prefs';shell('mkdir','-p',dest);adb('push',p,dest+'/'+name)
    uid=shell('stat','-c','%u','/data/user/0/'+package).strip()
    shell('chown','-R',uid+':'+uid,dest);shell('restorecon','-RF','/data/user/0/'+package)
def pref(package,name):return adb('exec-out','cat','/data/user/0/'+package+'/shared_prefs/'+name)

adb('wait-for-device');adb('root');adb('wait-for-device');shell('wm','dismiss-keyguard')
shell('wm','fixed-to-user-rotation','disabled');shell('wm','user-rotation','lock','0')
shell('settings','put','global','airplane_mode_on','1');shell('svc','wifi','disable');shell('svc','data','disable')
rows=json.loads(Path('guide-release/metadata.json').read_text())
titles=['통홈복귀','통스타그램','통SKY','통글로우']
for row,title in zip(rows,titles):
    package=re.search("name='([^']+)'",row['package']).group(1)
    component=package+'/.MainActivity';print('TEST',package,flush=True)
    adb('install',row['old']);shell('am','force-stop',package)
    seed(package,'guide-upgrade-proof.xml','<map><string name="kept">existing-data-20261105</string></map>')
    if package=='kr.homereturn':seed(package,'home.xml','<map><boolean name="enabled" value="false"/><boolean name="any_app" value="true"/></map>')
    if package=='kr.tonggwon.square':seed(package,'photo.xml','<map><int name="size" value="2160"/></map>')
    if package=='kr.tong.sky':
        seed(package,'settings.xml','<map><boolean name="testOffline" value="true"/></map>')
        seed(package,'startup-notice.xml','<map><boolean name="hidden" value="true"/></map>')
    shell('am','start','-W','-n',component);time.sleep(3);shell('am','force-stop',package)
    before=shell('dumpsys','package',package);installed=re.search(r'firstInstallTime=(.*)',before).group(1)
    assert 'Success' in adb('install','-r',row['file'])
    after=shell('dumpsys','package',package)
    assert installed==re.search(r'firstInstallTime=(.*)',after).group(1)
    assert 'existing-data-20261105' in pref(package,'guide-upgrade-proof.xml')
    shell('logcat','-c');shell('am','start','-W','-n',component);time.sleep(4)
    capture(package+'-before-help');tap('사용방법');wait(title+' · 사용방법');capture(package+'-guide-top')
    # Scroll a long guide; close remains visible and reachable.
    for i in range(5):shell('input','swipe','540','1500','540','650','300')
    wait('닫기');capture(package+'-guide-bottom')
    shell('wm','fixed-to-user-rotation','enabled');shell('wm','user-rotation','lock','1');time.sleep(2)
    assert dump().get('rotation') in ('1','3'), 'Device did not rotate to landscape'
    wait(title+' · 사용방법');wait('닫기');capture(package+'-guide-landscape')
    tap('닫기');assert node(title+' · 사용방법') is None
    shell('wm','user-rotation','lock','0');time.sleep(2)
    tap('사용방법');wait(title+' · 사용방법');shell('input','keyevent','4');time.sleep(1)
    assert node(title+' · 사용방법') is None,(package,'back did not close guide')
    if package=='kr.homereturn':
        data=pref(package,'home.xml');assert 'name="enabled" value="false"' in data and 'name="any_app" value="true"' in data
    if package=='kr.tonggwon.square':assert 'name="size" value="2160"' in pref(package,'photo.xml')
    assert 'existing-data-20261105' in pref(package,'guide-upgrade-proof.xml')
    logs=shell('logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in logs,logs
    (out/(package+'-package.txt')).write_text(after)
    print('PASS upgrade + offline guide + scrolling + rotation + close + back + unchanged settings',package,flush=True)
    shell('am','force-stop',package)
print('PASS ALL FOUR APP GUIDES AND EFFECTIVE ROTATION',flush=True)
