"""Genereert alle pixelart voor het blobje (16-bit stijl, Sweetie 16-palet).

Gebruik: python3 tools/generate_sprites.py
Schrijft PNG's naar app/src/main/res/drawable-nodpi/. Vervang een PNG gerust door eigen
pixelart (bijv. van OpenGameArt) met dezelfde afmetingen.
"""
from PIL import Image

# Sweetie 16 palet (GrafxKid) - typische 16-bit kleuren
P = {
 'K':(26,28,44),   'p':(93,39,93),  'r':(177,62,83), 'o':(239,125,87),
 'y':(255,205,117),'l':(167,240,112),'g':(56,183,100),'t':(37,113,121),
 'n':(41,54,111),  'b':(59,93,201), 'c':(65,166,246),'a':(115,239,247),
 'w':(244,244,244),'s':(148,176,194),'d':(86,108,134),'k':(51,60,87),
}

# Lichaamskleuren (licht, basis, schaduw): groen normaal, rood bij honger.
BODY = {'groen': ('l','g','t'), 'rood': ('o','r','p')}

def blob(squish=0, mood='happy', body='groen'):
    light, base, dark = BODY[body]
    W=H=32
    img=Image.new('RGBA',(W,H),(0,0,0,0))
    px=img.load()
    cx=15.5; bottom=28
    rx=11.6+squish; ry=12.0-squish
    cy=bottom-ry+2.5
    inside=set()
    for y in range(H):
        for x in range(W):
            dx=(x-cx)/rx; dy=(y-cy)/ry
            if y<=bottom and dx*dx+dy*dy<=1.0:
                inside.add((x,y))
    # losse uitsteeksels weghalen
    inside={(x,y) for (x,y) in inside if (x,y-1) in inside or (x,y+1) in inside}
    # schaduw op de grond
    for x in range(int(cx-rx+2),int(cx+rx)):
        px[x,bottom+1]=P['s']+(160,)
    for (x,y) in inside:
        edge=any((x+ox,y+oy) not in inside for ox,oy in ((1,0),(-1,0),(0,1),(0,-1)))
        if edge: px[x,y]=P['K']; continue
        dx=(x-cx)/rx; dy=(y-cy)/ry
        # licht van linksboven
        shade = dx*0.55 + dy*0.75
        if shade>0.55: c=dark
        elif shade<-0.55: c=light
        else: c=base
        px[x,y]=P[c]
    top=min(y for (_,y) in inside)
    # glans
    for (x,y) in [(9,top+3),(10,top+3),(8,top+4),(8,top+5),(11,top+2),(12,top+2)]:
        if (x,y) in inside: px[x,y]=P['w']
    ey=top+9
    def put(x,y,c):
        px[x,y]=P[c]
    L,R=11,19
    if mood=='happy':
        for x0 in (L,R):  # ^ ^ ogen
            put(x0,ey,'K'); put(x0+1,ey-1,'K'); put(x0+2,ey,'K')
        for x in range(14,18): put(x,ey+3,'K')
        put(13,ey+2,'K'); put(18,ey+2,'K')
        put(15,ey+4,'r'); put(16,ey+4,'r')
        for x0 in (L-2,R+3): put(x0,ey+2,'r'); put(x0+1,ey+2,'r')
    elif mood=='neutral':
        for x0 in (L,R):
            for dy in (0,1,2):
                put(x0,ey-1+dy,'K'); put(x0+1,ey-1+dy,'K')
            put(x0,ey-1,'w')
        for x in range(15,17): put(x,ey+3,'K')
    elif mood=='sad':
        for x0 in (L,R):
            for dy in (0,1):
                put(x0,ey+dy,'K'); put(x0+1,ey+dy,'K')
            put(x0,ey,'w')
        put(L,ey-2,'K'); put(L+1,ey-2,'K'); put(L+2,ey-3,'K')   # schuine wenkbrauwen
        put(R+2,ey-2,'K'); put(R+1,ey-2,'K'); put(R,ey-3,'K')
        for x in range(14,18): put(x,ey+3,'K')
        put(13,ey+4,'K'); put(18,ey+4,'K')
        put(R+1,ey+2,'a'); put(R+1,ey+3,'c')  # traan
    elif mood=='eat':
        for x0 in (L,R):
            put(x0,ey,'K'); put(x0+1,ey-1,'K'); put(x0+2,ey,'K')
        for y in range(ey+2,ey+5):
            for x in range(14,18): put(x,y,'K')
        put(15,ey+4,'r'); put(16,ey+4,'r')
    return img, top



def sprite(rows, w=None, h=None):
    rows=[r for r in rows.strip('\n').split('\n')]
    rows=[r.strip() for r in rows]
    W=w or max(len(r) for r in rows); H=h or len(rows)
    img=Image.new('RGBA',(W,H),(0,0,0,0)); px=img.load()
    for y,r in enumerate(rows):
        for x,ch in enumerate(r):
            if ch!='.': px[x,y]=P[ch]+(255,)
    return img

# Accessoires: (slot, x, y, kaart) - posities ten opzichte van frame A (bovenkant hoofd op rij 7)
ACC = {
'pet': ('hoofd', 7, 2, """
.....KKKKKKK......
...KKrrrrrrrKK....
..KrrrrrrrwrrrK...
.KrrrrrrrrrwrrrK..
.KrrrrrrrrrrrrrK..
KKKKKKKKKKKKKKKK..
KrrrrrrrrK........
KKKKKKKKKK........
"""),
'hoge_hoed': ('hoofd', 9, 0, """
..KKKKKKKKKK..
..KkkkkkkkdK..
..KkkkkkkkdK..
..KkkkkkkkdK..
..KrrrrrrrrK..
..KpppppppppK.
KKKkkkkkkkkKKK
KkkkkkkkkkkkdK
KKKKKKKKKKKKKK
"""),
'feestmuts': ('hoofd', 12, 0, """
...yy...
..yooy..
...KK...
...KbK..
..KyyyK.
..KbbbK.
.KyyyyyK
.KbbbbbK
KyyyyyyyK
KKKKKKKKK
"""),
'muts': ('hoofd', 8, 1, """
.......ww.......
......wwsw......
.....KKKKKK.....
...KKbbbbbbKK...
..KbbcbbbcbbbK..
.KbcbbbcbbbcbbK.
.KbbbbbbbbbbbbK.
KKnnnnnnnnnnnnKK
KnbnbnbnbnbnbnbK
KKKKKKKKKKKKKKKK
"""),
'ronde_bril': ('ogen', 6, 13, """
....KKK.....KKK....
...K...K...K...K...
KKKK...KKKKK...KKKK
...K...K...K...K...
....KKK.....KKK....
"""),
'nerdbril': ('ogen', 6, 13, """
...KKKKK...KKKKK...
...K...K...K...K...
KKKK...KKKKK...KKKK
...K...K...K...K...
...KKKKK...KKKKK...
"""),
'zonnebril': ('ogen', 6, 13, """
...KKKKKK.KKKKKK...
KKKKkwkkKKKkwkkKKKK
...KkkwkK.KkkwkK...
...KkkkkK.KkkkkK...
....KKKK...KKKK....
"""),
'hartjesbril': ('ogen', 6, 12, """
...KK.KK...KK.KK...
..KrrKrrK.KrrKrrK..
KKKrwrrrKKKrwrrrKKK
..KrrrrrK.KrrrrrK..
...KrrrK...KrrrK...
....KrK.....KrK....
.....K.......K.....
"""),
'rode_sjaal': ('nek', 4, 21, """
KKKKKKKKKKKKKKKKKKKKKKKK
KrrrrrrrrrrrrrrrrrrrrrrK
KrprrprrprrprrprrprrprrK
KKKKKKKKKKKKKKKrrrKKKKKK
...............KrrK.....
...............KrprK....
...............KrrrK....
...............KpKpK....
"""),
'streepjessjaal': ('nek', 4, 21, """
KKKKKKKKKKKKKKKKKKKKKKKK
KbbwwbbwwbbwwbbwwbbwwbbK
KbbwwbbwwbbwwbbwwbbwwbbK
KKKKKKKKKKKKKKKbbbKKKKKK
...............KwwK.....
...............KbbbK....
...............KwwwK....
...............KbKbK....
"""),
'gouden_pollepel': ('hand', 23, 1, """
..KKKK...
.KyyyyK..
KywkkyoK.
KykkkkoK.
KykkkkoK.
KyykkooK.
.KyyooK..
..KKoK...
...KyK...
...KyK...
...KyK...
...KyK...
...KyK...
...KyK...
...KyK...
...KyK...
..KKyKK..
.KllKgK..
.KggggK..
..KKKK...
"""),
}

FOOD = {
'brood': """
................
................
....KKKKKKK.....
..KKyyyyyyyKK...
.KyyowyyyyyyyK..
KyyowyyowyyyyoK.
KyyyyyyowyyyyoK.
KoyyyyyyyyyyooK.
KooyyyyyyyyyooK.
.KooooooooooooK.
.KoooooooooooK..
..KKKKKKKKKKK...
................
................
................
................
""",
'drankje': """
................
.........KK.....
........KwK.....
.......KwK......
....KKKKKKKK....
....KssssssK....
....KsaaaasK....
....KcwaaacK....
....KcwaaacK....
....KcaaaacK....
....KcaaaacK....
....KccccccK....
....KbbbbbbK....
.....KKKKKK.....
................
................
""",
'wortel': """
...........KK...
.........KKlgK..
........KlgK....
......KKKgKKKK..
.....KooKKlgggK.
....KoyooK.KKK..
....KoooorK.....
...KoyoorK......
...KooorK.......
..KoooorK.......
..KoorK.........
.KoorK..........
.KorK...........
.KKK............
................
................
""",
'pizza': """
................
.KKKKKKKKKKKKK..
KoooooooooooooK.
KyyyyyyyyyyyyyK.
.KyrryyyyyrryK..
.KyrryyyyyrryK..
..KyyyyrryyyK...
..KyyyyrryyyK...
...KylyyyyyK....
....KyyrryK.....
....KyyrryK.....
.....KyyyK......
......KyK.......
.......K........
................
................
""",
'patat': """
................
....KK.KK.KK....
...KyyKyyKyyK...
...KyyKyyKyyK...
..KyKyyKyyKyyK..
..KyyKyyKyKyyK..
..KKKKKKKKKKKK..
..KrrrrrrrrrrK..
..KrwwrrrrrrrK..
...KrwwwwrrrK...
...KrrwwrrrrK...
...KrrrrrrrrK...
....KrrrrrrK....
....KKKKKKKK....
................
................
""",
'macaroni': """
................
................
....KK..KK......
...KyoK.KyoK.K..
..KyyKKyoKKyoK..
.KyoKyyKyyoKyyK.
KKyyoKyoKyyoKyKK
KwwwwwwwwwwwwwwK
KsKyyoKyoKyyoKsK
.KsssssssssssK..
.KwwwwwwwwwwwwK.
..KsssssssssssK.
...KKKKKKKKKKK..
................
................
................
""",
'pannenkoek': """
................
................
......KKK.......
.....KyywK......
...KKKyyyKKKK...
..KyyyoyyyyyyK..
.KoooooooooooK..
.KyyyyyoyyyyyyK.
KoooooooooooooK.
KyyyyyyyyyoyyyK.
KooooooooooooooK
.KwwwwwwwwwwwwK.
.KssssssssssssK.
..KKKKKKKKKKKK..
................
................
""",
}


def background():
    W,H=64,48
    img=Image.new('RGBA',(W,H),P['w']+(255,)); px=img.load()
    for y in range(34):
        for x in range(W):
            px[x,y]=P['a' if (x//4)%2==0 else 'w']+(255,)
    for x in range(W): px[x,33]=P['s']+(255,); px[x,34]=P['d']+(255,)
    # raam
    for y in range(5,21):
        for x in range(4,22):
            edge = x in (4,21) or y in (5,20)
            px[x,y]=P['K' if edge else ('w' if x in (5,20) or y in (6,19) or x==12 or x==13 or y==12 else 'c')]+(255,)
    for (x,y) in [(7,8),(8,8),(9,8),(6,9),(7,9),(8,9),(9,9),(10,9)]: px[x,y]=P['w']+(255,)
    for (x,y) in [(16,15),(17,15),(18,15),(15,16),(16,16),(17,16),(18,16),(19,16)]: px[x,y]=P['w']+(255,)
    # schilderij
    for y in range(8,18):
        for x in range(46,58):
            edge = x in (46,57) or y in (8,17)
            px[x,y]=P['K' if edge else ('y' if x in (47,56) or y in (9,16) else ('l' if y>13 else 'b'))]+(255,)
    for (x,y) in [(52,11),(53,11),(52,12),(53,12)]: px[x,y]=P['y']+(255,)
    # houten vloer
    for y in range(35,H):
        for x in range(W):
            plank=(y-35)//4
            c='o' if (y-35)%4 else 'r'
            if (x+plank*13)%24==0 and (y-35)%4: c='r'
            px[x,y]=P[c]+(255,)
    # kleed
    for y in range(38,47):
        for x in range(10,54):
            dx=(x-31.5)/22; dy=(y-42.5)/4.6
            if dx*dx+dy*dy<=1: px[x,y]=P['p' if dx*dx+dy*dy>0.7 else 'b']+(255,)
    return img

COIN = """
..KKKK..
.KyyyyK.
KywyyyoK
KyyKoyoK
KyyKoyoK
KyyyyyoK
.KoooKK.
..KKKK..
"""

HEART = """
.KK.KK.
KrrKrrK
KrwrrrK
.KrrrK.
..KrK..
...K...
"""

def export(out):
    import os
    os.makedirs(out, exist_ok=True)
    for mood in ('happy','neutral','sad','eat'):
        for f,sq in ((0,0),(1,1)):
            img,_=blob(sq,mood); img.save(f'{out}/pet_blob_{mood}_{f}.png')
    # Honger: rood en verdrietig
    for f,sq in ((0,0),(1,1)):
        img,_=blob(sq,'sad','rood'); img.save(f'{out}/pet_blob_hungry_{f}.png')
    for name,(slot,x,y,m) in ACC.items():
        full=Image.new('RGBA',(32,32),(0,0,0,0)); full.alpha_composite(sprite(m),(x,y))
        full.save(f'{out}/pet_acc_{name}.png')
    for name,m in FOOD.items():
        sprite(m,16,16).save(f'{out}/pet_food_{name}.png')
    background().save(f'{out}/pet_background.png')
    sprite(COIN).save(f'{out}/pet_coin.png')
    sprite(HEART).save(f'{out}/pet_heart.png')

if __name__ == '__main__':
    import os, sys
    here=os.path.dirname(os.path.abspath(__file__))
    export(sys.argv[1] if len(sys.argv)>1 else os.path.join(here,'..','app','src','main','res','drawable-nodpi'))
