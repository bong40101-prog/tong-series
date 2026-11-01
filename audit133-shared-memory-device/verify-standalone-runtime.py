#!/usr/bin/env python3
import pathlib,json,re,sys
root=pathlib.Path(sys.argv[1]);r=json.loads((root/'memory-device-report.json').read_text());log=(root/'logcat.txt').read_text(errors='replace');pid=r['pid'];filtered='\n'.join(line for line in log.splitlines() if re.search(r'\s'+str(pid)+r'\s',line))
assert r['api']==33 and r['runtimeMaxHeapBytes']==192*1024*1024 and not r['networkPermission']
assert r['success'] and r['standalone'] and len(r['cases'])==3 and all(c['passed'] for c in r['cases'])
assert 'FATAL EXCEPTION:' not in filtered and 'java.lang.OutOfMemoryError' not in filtered and 'ANR in kr.tong.sky' not in log
for i,c in enumerate(r['cases'][:2]):
 p=c['preserved'];assert p['originalRecords']==1194 and p['previousPreserved']+p['olderPreviousReplacedByExpectedAdvance']==911 and p['allFingerprintsVerified'] and p['seedPreserved'];assert c['planning']['keAbsent'] and c['planning']['fullPlanning'] and c['planning']['completedBrowserQueries']>=1 and c['planning']['checkpointWrites']>=2 and c['planning']['plannedQueries']>2;assert c['dashboard']['rendered'] and c['dashboard']['airportsWithCurrentData']>0
assert r['cases'][0]['planning']['actualAllBatchKeys'] and r['cases'][1]['planning']['actualAllBatchKeys'] is None
assert r['cases'][2]['queryMonth']>='2027-01' and r['cases'][2]['preserved']['firstCalendarComplete']
out={'schema':1,'api':33,'heapBytes':r['runtimeMaxHeapBytes'],'passed':True,'elapsedMs':r['elapsedMs'],'sampledUsedBytes':r['sampledUsedBytes'],'unexpectedFatalCount':0,'anrCount':0,'cases':r['cases'],'scope':'Fixed-only standalone main planning and first real calendar/checkpoint. Both OZ full-world and Europe queues were intentionally stopped after initial progress. KE future-month case collects one January2027 calendar. Local fixtures only, INTERNET removed; no claim of completing the full real world/region scan.'}
(root/'verified-runtime.json').write_text(json.dumps(out,indent=2,ensure_ascii=False)+'\n');(root/'filtered-logcat.txt').write_text(filtered+'\n');print('OFFLINE_STANDALONE_API33_VERIFIED',r['elapsedMs'])
