#!/usr/bin/env python3
"""Tekent het app-icoon (het Pil-maatje) als pixelart en schrijft de drie lagen van het adaptive icon.

Elke pixel wordt een rechthoekje in een vector drawable van 108x108 dp, samengevoegd per kleur en per rij,
zodat het icoon op elk scherm scherp blijft. De capsule wordt belicht als een 3D-cilinder (licht van linksboven)
en de kleuren worden afgerond op de palet-trappen uit de pixelstijl van de app.

Gebruik: python3 android/tools/generate_launcher_icon.py
"""
import math, os
N=48; U=66/N; O=21
K='#1a1200'
GOLD=['#3d2a00','#a8740a','#d1951a','#f0b429','#ffd86b','#ffe9a0','#fff6d0']   # outline, deep..spec
GREEN=['#0f3d1a','#1c6b2b','#25843a','#2f9e44','#4fbf5e','#6fd67a','#d8ffd8']
cx,cy=24.0,24.5; ang=math.radians(-35); half=13.0; R=10.6
ux,uy=math.cos(ang),math.sin(ang)
L=(-0.45,-0.75,0.5); ln=math.sqrt(sum(a*a for a in L)); L=tuple(a/ln for a in L)
def geo(x,y):
    px,py=x+.5,y+.5; dx,dy=px-cx,py-cy
    t=max(-half,min(half,dx*ux+dy*uy)); qx,qy=cx+t*ux,cy+t*uy
    vx,vy=px-qx,py-qy; d=math.hypot(vx,vy)
    return d, vx,vy, dx*ux+dy*uy
fg=[[None]*N for _ in range(N)]
for y in range(N):
    for x in range(N):
        d,vx,vy,along=geo(x,y)
        if d>R: continue
        nz=math.sqrt(max(0,1-(d/R)**2)); nx,ny=vx/R,vy/R
        diff=max(0,nx*L[0]+ny*L[1]+nz*L[2])
        # specular (view dir 0,0,1)
        rz=2*diff*nz-L[2]; spec=max(0,rz)**18
        rim=max(0,(nx*0.5+ny*0.8))*(1-nz)  # reflected light at lower-right edge
        pal=GREEN if along<0 else GOLD
        v=0.26+0.8*diff+0.25*rim
        if spec>0.55: idx=6
        elif v>0.92: idx=5
        elif v>0.74: idx=4
        elif v>0.52: idx=3
        elif v>0.34: idx=2
        else: idx=1
        fg[y][x]=pal[idx]
        # seam
        if abs(along)<0.8: fg[y][x]=pal[0] if along<0 else GOLD[0]
        elif 0.8<=along<1.8: fg[y][x]=GOLD[min(6,idx+1)]   # bevel light just right of seam
        elif -1.8<along<=-0.8: fg[y][x]=GREEN[max(1,idx-1)]
# outline (selout): edge pixels -> dark outline; lit top-left edge uses material outline colour, rest K
out=[r[:] for r in fg]
for y in range(N):
    for x in range(N):
        if fg[y][x] is None: continue
        edge=any(not(0<=x+a<N and 0<=y+b<N) or fg[y+b][x+a] is None for a,b in((1,0),(-1,0),(0,1),(0,-1)))
        if edge:
            d,vx,vy,along=geo(x,y)
            lit=(vx*L[0]+vy*L[1])>0
            out[y][x]=(GREEN[0] if along<0 else GOLD[0]) if lit else K
fg=out
# face (upright) on gold half
fx=cx+7.6*ux; fy=cy+7.6*uy; FX,FY=int(round(fx)),int(round(fy))
face={}
def put(x,y,c): face[(x,y)]=c
for ex in (FX-5, FX+3):
    for a in range(3):
        for b in range(4): put(ex+a,FY-3+b,K)
    put(ex,FY-3,'#ffffff'); put(ex+1,FY-3,'#ffffff')
    put(ex+2,FY-1,'#73c2f7'); put(ex+1,FY,'#3b5dc9')
# mouth: small open smile
for (a,b) in [(-2,2),(2,2),(-1,3),(0,3),(1,3)]: put(FX+a,FY+b,K)
put(FX-1,FY+2,'#c0392b'); put(FX,FY+2,'#c0392b'); put(FX+1,FY+2,'#c0392b')
put(FX,FY+3,'#ff8a7a'); put(FX,FY+3,K)
# blush
for (a,b,c) in [(-8,2,'#ff8a7a'),(-7,2,'#e05a8a'),(-6,2,'#ff8a7a'),(5,2,'#ff8a7a'),(6,2,'#e05a8a'),(7,2,'#ff8a7a')]: put(FX+a,FY+b,c)
for (x,y),c in face.items():
    if fg[y][x] is not None and fg[y][x] not in (K,GOLD[0]): fg[y][x]=c
# ---- background: blue gradient + radial glow, Bayer-dithered
BL=['#141c4a','#1f2a6b','#2b3f9a','#3b5dc9','#4f7fe0','#73c2f7']
B4=[[0,8,2,10],[12,4,14,6],[3,11,1,9],[15,7,13,5]]
def bgc(x,y):
    px,py=O+(x+.5)*U, O+(y+.5)*U
    t=1-py/108
    g=math.exp(-((px-50)**2+(py-48)**2)/(2*26**2))
    v=0.5+1.6*t+2.2*g   # 0..~4.5
    v=max(0,min(len(BL)-1.001,v))
    lo=int(v); f=v-lo
    if 0.42<f<0.58: return BL[lo+1] if (x+y)%2 else BL[lo]
    return BL[lo+1] if f>=0.58 else BL[lo]
lo,hi=-16,64
bg={}
for y in range(lo,hi):
    for x in range(lo,hi): bg[(x,y)]=bgc(x,y)
def spark(cx_,cy_,size,col,core):
    for i in range(-size,size+1):
        bg[(cx_+i,cy_)]=col; bg[(cx_,cy_+i)]=col
    bg[(cx_,cy_)]=core
spark(7,9,3,'#ffe08a','#ffffff'); spark(40,39,2,'#ffe08a','#ffffff'); spark(3,19,1,'#73c2f7','#ffffff'); spark(40,-4,1,'#73c2f7','#ffffff'); spark(10,44,1,'#ffe08a','#ffffff')
def runs(get,lo,hi):
    r={}
    for y in range(lo,hi):
        x=lo
        while x<hi:
            c=get(x,y)
            if c is None: x+=1; continue
            x2=x+1
            while x2<hi and get(x2,y)==c: x2+=1
            r.setdefault(c,[]).append((x,y,x2-x)); x=x2
    return r
def path(rs,ov=0.06):
    return ''.join(f'M{O+x*U:.3f},{O+y*U:.3f}h{w*U:.3f}v{U+ov:.3f}h{-w*U:.3f}z' for x,y,w in rs)
fgr=runs(lambda x,y:fg[y][x],0,N)
bgr=runs(lambda x,y:bg[(x,y)],lo,hi)
# mono: silhouette minus seam and face dark features
def mono(x,y):
    c=fg[y][x]
    if c is None: return None
    d,vx,vy,along=geo(x,y)
    if abs(along)<0.8 and d<R-1.2: return None
    if (x,y) in face and face[(x,y)] in (K,'#c0392b','#3b5dc9','#73c2f7') : return None
    return 'M'
mr=runs(mono,0,N)
RES=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','app','src','main','res','drawable')
def argb(c): return '#FF'+c[1:].upper()
def vector(comment,layers):
    body=''.join(f'    <path\n        android:fillColor="{c}"\n        android:pathData="{d}" />\n' for c,d in layers)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} Gegenereerd door tools/generate_launcher_icon.py. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'+body+'</vector>\n')
def write(name,text):
    with open(os.path.join(RES,name),'w',encoding='utf-8') as f: f.write(text)
write('ic_launcher_background.xml',vector('Achtergrond van het app-icoon: blauwe gloed met geditherde overgangen en fonkelingen.',
      [(argb(c),path(v)) for c,v in bgr.items()]))
write('ic_launcher_foreground.xml',vector('Pil-maatje: gedetailleerde pixelcapsule (48x48) met gezichtje, binnen de veilige zone van 66x66 dp.',
      [(argb(c),path(v)) for c,v in fgr.items()]))
write('ic_launcher_monochrome.xml',vector('Eenkleurig silhouet van het Pil-maatje voor thema-iconen (Android 13+).',
      [('#FFFFFFFF',path(mr['M']))]))
