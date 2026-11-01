#!/usr/bin/env python3
import sys,pathlib,json,re,hashlib
mode=sys.argv[1];root=pathlib.Path(sys.argv[2]);r=json.loads((root/'memory-device-report.json').read_text());log=(root/'logcat.txt').read_text(errors='replace');pid=r['pid'];filtered='\n'.join(line for line in log.splitlines() if re.search(r'\s'+str(pid)+r'\s',line))
assert r['api']==33 and r['runtimeMaxHeapBytes']==192*1024*1024 and not r['networkPermission']
assert r['fixtures']['documents']['OZ']['records']==1194 and r['fixtures']['documents']['OZ']['previous']==911
if mode=='baseline':
 assert 'java.lang.OutOfMemoryError' in filtered,'Old-version OOM was not observed; do not claim reproduced'
 assert 'kr.tong.sky.GitHubStore.unpack' in filtered or 'kr.tong.sky.GitHubStore.read' in filtered,'Observed old OOM lacks production decoder stack'
 assert 'kr.tong.sky.SharedCache' in filtered,'Observed old OOM lacks shared-cache stack'
 assert 'FATAL EXCEPTION:' in filtered,'Expected actual worker FATAL absent'
 outcome={'schema':1,'oldVersionOomReproduced':True,'api':33,'pid':pid,'heapBytes':r['runtimeMaxHeapBytes'],'lastPhase':r['phase'],'expectedFatal':True,'codecStackObserved':True,'fixtureManifest':r['fixtures']}
else:
 assert r['success'] and len(r['cases'])==1 and r['cases'][0]['passed']
 assert 'FATAL EXCEPTION:' not in filtered and 'java.lang.OutOfMemoryError' not in filtered and 'ANR in kr.tong.sky' not in log
 assert r['cases'][0]['OZ']['importedRecords']==1194 and r['cases'][0]['OZ']['previousPreserved']==911
 assert r['cases'][0]['KE']['newBrowserCalendars']==2 and r['cases'][0]['OZ']['newBrowserCalendars']==2
 outcome={'schema':1,'fixedVersionPassed':True,'api':33,'pid':pid,'heapBytes':r['runtimeMaxHeapBytes'],'elapsedMs':r['elapsedMs'],'sampledUsedBytes':r['sampledUsedBytes'],'unexpectedFatalCount':0,'anrCount':0,'case':r['cases'][0],'fixtureManifest':r['fixtures']}
(root/'verified-runtime.json').write_text(json.dumps(outcome,indent=2,ensure_ascii=False)+'\n')
(root/'filtered-logcat.txt').write_text(filtered+'\n')
print(json.dumps({'mode':mode,'verified':True,'pid':pid,'heap':r['runtimeMaxHeapBytes']}))
