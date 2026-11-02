import sys,json,sqlite3,datetime,calendar
from pathlib import Path

root=Path(sys.argv[2]);root.mkdir(parents=True,exist_ok=True)
def connect(name):return sqlite3.connect(root/name)
def rows(db,table):return db.execute('SELECT * FROM '+table).fetchall()
if sys.argv[1]=='seed':
    now=datetime.datetime.now(datetime.timezone.utc).isoformat()
    days=[]
    for day in range(1,32):
        d={'date':f'2027-01-{day:02}','status':'OBSERVED'}
        d.update(dict.fromkeys(['economyAward','premiumAward','prestigeAward','premiumUpgrade','prestigeUpgrade','firstCombined'],False));days.append(d)
    oz={'carrier':'OZ','origin':'ICN','destination':'JFK','month':'2027-01','sourceUpdatedAt':now,'collectedAt':now,'dates':days}
    for name,carrier in [('tong-sky.db','KE'),('tong-sky-asiana.db','OZ')]:
        with connect(name) as db:
            db.executescript('CREATE TABLE batches(k TEXT PRIMARY KEY,spec TEXT NOT NULL);CREATE TABLE watches(id INTEGER PRIMARY KEY AUTOINCREMENT,spec TEXT NOT NULL);CREATE TABLE snapshots(k TEXT PRIMARY KEY,data TEXT NOT NULL,previous TEXT,success INTEGER NOT NULL);CREATE TABLE errors(k TEXT PRIMARY KEY,message TEXT NOT NULL,attempted INTEGER NOT NULL);CREATE TABLE shared_outbox(scope TEXT NOT NULL,k TEXT NOT NULL,token TEXT NOT NULL,payload TEXT NOT NULL,queued_at INTEGER NOT NULL,PRIMARY KEY(scope,k));CREATE TABLE operation_migrations(k TEXT PRIMARY KEY,policy TEXT NOT NULL);PRAGMA user_version=7;')
            db.execute('INSERT INTO snapshots VALUES(?,?,?,?)',('ICN-JFK-2027-01',json.dumps(oz) if carrier=='OZ' else 'old-malformed-calendar',None,1))
            db.execute('INSERT INTO errors VALUES(?,?,?)',('old-'+carrier,'saved error',1))
            db.execute('INSERT INTO watches VALUES(?,?)',(7,'{"preserveSetting":true}'))
            db.execute('INSERT INTO shared_outbox VALUES(?,?,?,?,?)',(carrier+'-scope','old','00000000-0000-0000-0000-000000000000','{}',1))
            db.execute('INSERT INTO batches VALUES(?,?)',('OZ-ICN-유럽','{"carrier":"OZ","cursor":19}'))
            if carrier=='KE':db.execute('INSERT INTO batches VALUES(?,?)',('ICN-유럽','{"carrier":"KE","cursor":12}'))
    with connect('tong-sky-repeat-decisions.db') as db:
        db.executescript('CREATE TABLE decisions(id TEXT PRIMARY KEY,origin TEXT NOT NULL,destination TEXT NOT NULL,data TEXT NOT NULL,recorded_at INTEGER NOT NULL);PRAGMA user_version=1;')
        db.execute('INSERT INTO decisions VALUES(?,?,?,?,?)',('old','ICN','CDG','{}',1))
    with connect('tong-sky-diagnostics.db') as db:
        db.executescript('CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,carrier TEXT NOT NULL,event TEXT NOT NULL,code TEXT NOT NULL,payload TEXT NOT NULL);CREATE TABLE active(carrier TEXT PRIMARY KEY,session TEXT NOT NULL,payload TEXT NOT NULL);CREATE TABLE imported(carrier TEXT PRIMARY KEY);PRAGMA user_version=1;')
        for carrier in ['KE','OZ']:
            db.execute('INSERT INTO events(carrier,event,code,payload) VALUES(?,?,?,?)',(carrier,'saved','FIXTURE','{"carrier":"'+carrier+'"}'))
            db.execute('INSERT INTO imported VALUES(?)',(carrier,))
    print('seeded KE and OZ database fixtures')
elif sys.argv[1]=='check-reset':
    with connect('tong-sky.db') as db:
        for table in ['snapshots','errors','shared_outbox']:assert not rows(db,table),table
        assert rows(db,'batches')==[('OZ-ICN-유럽','{"carrier":"OZ","cursor":19}')]
        assert rows(db,'watches')==[(7,'{"preserveSetting":true}')]
    with connect('tong-sky-asiana.db') as db:
        for table in ['snapshots','errors','shared_outbox','batches','watches']:assert len(rows(db,table))==1,table
        assert json.loads(rows(db,'snapshots')[0][1])['carrier']=='OZ'
    with connect('tong-sky-repeat-decisions.db') as db:assert not rows(db,'decisions')
    with connect('tong-sky-diagnostics.db') as db:
        assert db.execute("SELECT COUNT(*) FROM events WHERE carrier='KE'").fetchone()[0]==0
        assert db.execute("SELECT COUNT(*) FROM events WHERE carrier='OZ'").fetchone()[0]==1
        assert ('OZ',) in rows(db,'imported')
    print('PASS: KE reset; OZ calendars, shared work, settings and history retained')
elif sys.argv[1]=='fresh':
    with connect('tong-sky.db') as db:db.execute("INSERT INTO errors VALUES('fresh-ke','new observation after reset',2)")
    with connect('tong-sky-repeat-decisions.db') as db:db.execute("INSERT INTO decisions VALUES('fresh','ICN','CDG','{}',2)")
elif sys.argv[1]=='check-fresh':
    with connect('tong-sky.db') as db:assert db.execute("SELECT message FROM errors WHERE k='fresh-ke'").fetchone()==('new observation after reset',)
    with connect('tong-sky-repeat-decisions.db') as db:assert db.execute("SELECT COUNT(*) FROM decisions WHERE id='fresh'").fetchone()[0]==1
    print('PASS: second launch preserves newly saved KE records')
