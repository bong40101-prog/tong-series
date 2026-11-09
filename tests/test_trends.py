import importlib.util,json,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('trends',Path(__file__).parents[1]/'scripts/update-trends.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class TrendsTests(unittest.TestCase):
 def item(self,title,ids):return {'title':title,'traffic':'1000','categoryIds':ids,'related':[]}
 def test_filter_before_limit(self):
  items=[self.item('경기'+str(i),[17]) for i in range(25)]+[self.item('음식'+str(i),[5]) for i in range(22)]
  d=m.build_payload(items,items);self.assertEqual(len(d['groups']),10);food=d['groups'][1];self.assertEqual(food['totalAvailable'],22);self.assertEqual(len(food['items']),20);self.assertEqual(food['items'][0]['title'],'음식0')
 def test_real_short_lists_and_empty_camping(self):
  d=m.build_payload([self.item('기아',[1])],[self.item('기아',[1])]);groups={g['id']:g for g in d['groups']};self.assertEqual(len(groups['cars']['items']),1);self.assertEqual(groups['camping']['items'],[]);self.assertEqual(groups['camping']['status'],'ok')
 def test_camping_uses_related_terms_not_all_leisure(self):
  items=[self.item('골프',[8]),{**self.item('장소',[19]),'related':['가을 캠핑장']}];d=m.build_payload(items,items);self.assertEqual(d['groups'][3]['items'][0]['title'],'장소');self.assertEqual(len(d['groups'][3]['items']),1)
 def test_partial_failure_preserves_timestamp_and_list(self):
  old=m.build_payload([self.item('old',[5])],[self.item('old',[5])],now='2026-11-08T00:00:00Z');new=m.build_payload([self.item('new',[1])],None,old,now='2026-11-09T00:00:00Z');self.assertEqual(new['groups'][1]['updatedAt'],'2026-11-08T00:00:00Z');self.assertTrue(new['groups'][1]['refreshFailed']);self.assertEqual(new['groups'][0]['items'][0]['title'],'new')
 def test_invalid_structure_fails_instead_of_erasing(self):
  for raw in ['bad',"AF_initDataCallback({key: 'ds:0',data:[0,[]]}"]:
   with self.assertRaises(ValueError):m.parse_page(raw)
 def test_parser_preserves_all_items_and_deduplicates(self):
  rows=[[str(i),None,'KR',[],None,None,100,None,None,[str(i)],[5]] for i in range(30)];raw="AF_initDataCallback({key: 'ds:0',data:"+json.dumps([None,rows+[rows[0]]])+"})";self.assertEqual(len(m.parse_page(raw)),30)
 def test_total_network_failure_does_not_write_file(self):
  with tempfile.TemporaryDirectory() as t:
   p=Path(t)/'trends.json';p.write_text('{"original":true}')
   def fail(url):raise OSError('offline')
   with self.assertRaises(RuntimeError):m.update(p,fail)
   self.assertEqual(p.read_text(),'{"original":true}')
if __name__=='__main__':unittest.main()
