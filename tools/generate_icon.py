#!/usr/bin/env python3
"""Deterministic native Android icon, SVG master and TV banner. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
OUTER='M38,28 C34.4,25.9 30,28.5 30,32.7 L30,75.3 C30,79.5 34.4,82.1 38,80 L79,58.7 C82.6,56.6 82.6,51.4 79,49.3 Z'
INNER='M43,42.7 C42.2,42.2 41.2,42.8 41.2,43.8 L41.2,64.2 C41.2,65.2 42.2,65.8 43,65.3 L60.8,55.1 C61.6,54.6 61.6,53.4 60.8,52.9 Z'
FOLD='M34,72 L73,50 L79,49.3 C82.6,51.4 82.6,56.6 79,58.7 L38,80 C36.7,80.7 35.3,80.8 34,80.5 Z'
GLOW='M33,33 C33,30.5 35.2,29.7 37,30.7 L71,50'
BG='M18,0 L54,0 C64,0 72,8 72,18 L72,54 C72,64 64,72 54,72 L18,72 C8,72 0,64 0,54 L0,18 C0,8 8,0 18,0 Z'
HEADER='<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
mark=f'''<path android:pathData="{OUTER} {INNER}" android:fillType="evenOdd">
<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="30" android:startY="28" android:endX="30" android:endY="81"><item android:offset="0" android:color="#B2FFE9"/><item android:offset="0.5" android:color="#72DCC4"/><item android:offset="1" android:color="#25ADA7"/></gradient></aapt:attr></path>
<path android:pathData="{FOLD}" android:fillColor="#064E61" android:fillAlpha="0.18"/>
<path android:pathData="{GLOW}" android:fillColor="#00000000" android:strokeColor="#FFFFFF" android:strokeAlpha="0.28" android:strokeWidth="0.8" android:strokeLineCap="round"/>'''
(RES/'drawable/ic_launcher_foreground.xml').write_text(HEADER+mark+'</vector>')
legacy=f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="72dp" android:height="72dp" android:viewportWidth="72" android:viewportHeight="72"><path android:pathData="{BG}"><aapt:attr name="android:fillColor"><gradient android:startX="0" android:startY="0" android:endX="0" android:endY="72" android:startColor="#1A3344" android:endColor="#0A1422" android:type="linear"/></aapt:attr></path><group android:translateX="-18" android:translateY="-18">{mark}</group></vector>'''
(RES/'drawable/app_icon.xml').write_text(legacy)
(RES/'drawable/ic_launcher_background.xml').write_text('''<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><gradient android:angle="270" android:startColor="#1A3344" android:endColor="#0A1422"/></shape>''')
(RES/'drawable/ic_launcher_monochrome.xml').write_text(HEADER+f'<path android:pathData="{OUTER} {INNER}" android:fillType="evenOdd" android:fillColor="#FFFFFF"/></vector>')
adaptive='''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/>{}</adaptive-icon>'''
(RES/'mipmap-anydpi-v26/ic_launcher.xml').write_text(adaptive.format(''))
(RES/'mipmap-anydpi-v33/ic_launcher.xml').write_text(adaptive.format('<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>'))
(RES/'mipmap-anydpi').mkdir(exist_ok=True)
(RES/'mipmap-anydpi/ic_launcher.xml').write_text(legacy)
(RES/'values/icons.xml').unlink(missing_ok=True)
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72"><defs><linearGradient id="bg" x1="0" x2="0" y1="0" y2="1"><stop stop-color="#1A3344"/><stop offset="1" stop-color="#0A1422"/></linearGradient><linearGradient id="mint" x1="30" y1="28" x2="30" y2="81" gradientUnits="userSpaceOnUse"><stop stop-color="#B2FFE9"/><stop offset="0.5" stop-color="#72DCC4"/><stop offset="1" stop-color="#25ADA7"/></linearGradient></defs><rect x="18" y="18" width="72" height="72" rx="18" fill="url(#bg)"/><path d="{OUTER} {INNER}" fill-rule="evenodd" fill="url(#mint)"/><path d="{FOLD}" fill="#064E61" opacity="0.18"/><path d="{GLOW}" fill="none" stroke="white" stroke-opacity="0.28" stroke-width="0.8" stroke-linecap="round"/></svg>'''
(ROOT/'design/app-icon.svg').write_text(svg)
# Sample exactly the same M/L/C/Z geometry for previews; Android uses the vector above.
import re
def points(path, scale):
    words=re.findall(r'[MLCZ]|-?\d+(?:\.\d+)?',path);i=0;result=[];last=(0,0)
    def p(x,y):return ((x-18)*scale,(y-18)*scale)
    while i<len(words):
        command=words[i];i+=1
        if command in ('M','L'):
            last=(float(words[i]),float(words[i+1]));i+=2;result.append(p(*last))
        elif command=='C':
            x0,y0=last;x1,y1,x2,y2,x3,y3=map(float,words[i:i+6]);i+=6
            for j in range(1,33):
                t=j/32;u=1-t
                result.append(p(u**3*x0+3*u*u*t*x1+3*u*t*t*x2+t**3*x3,u**3*y0+3*u*u*t*y1+3*u*t*t*y2+t**3*y3))
            last=(x3,y3)
    return result
size=1024;scale=size/72
im=Image.new('RGBA',(size,size));bg=Image.new('RGBA',(size,size));draw=ImageDraw.Draw(bg)
def blend(a,b,t):return tuple(round(x+(y-x)*t) for x,y in zip(a,b))
for y in range(size):draw.line((0,y,size,y),fill=blend((26,51,68),(10,20,34),y/(size-1)))
mask=Image.new('L',(size,size));ImageDraw.Draw(mask).rounded_rectangle((0,0,size-1,size-1),radius=18*scale,fill=255)
im.paste(bg,(0,0),mask)
mark_mask=Image.new('L',(size,size));d=ImageDraw.Draw(mark_mask);d.polygon(points(OUTER,scale),fill=255);d.polygon(points(INNER,scale),fill=0)
mint=Image.new('RGBA',(size,size));d=ImageDraw.Draw(mint)
for y in range(size):
    t=max(0,min(1,((y/scale+18)-28)/53))
    color=blend((178,255,233),(114,220,196),t*2) if t<=.5 else blend((114,220,196),(37,173,167),(t-.5)*2)
    d.line((0,y,size,y),fill=color)
im.paste(mint,(0,0),mark_mask)
layer=Image.new('RGBA',(size,size));d=ImageDraw.Draw(layer);d.polygon(points(FOLD,scale),fill=(6,78,97,46));d.line(points(GLOW,scale),fill=(255,255,255,72),width=max(1,round(.8*scale)),joint='curve');im=Image.alpha_composite(im,layer)
preview=im.resize((512,512),Image.Resampling.LANCZOS);preview.save(ROOT/'design/app-icon-preview.png')
# TV launcher banner uses the same mark; no typography inside the launcher icon.
banner=Image.new('RGB',(320*3,180*3),'#0c1826');banner.paste(im.resize((120*3,120*3),Image.Resampling.LANCZOS),(12*3,30*3),im.resize((120*3,120*3),Image.Resampling.LANCZOS))
d=ImageDraw.Draw(banner);font=ImageFont.truetype('DejaVuSans-Bold.ttf',22*3);small=ImageFont.truetype('DejaVuSans.ttf',10*3)
d.text((145*3,72*3),'VideoPlayer',font=font,fill='#f3fcfa',anchor='lm');d.text((146*3,103*3),'ANDROID TV',font=small,fill='#72dcc4',anchor='lm')
banner.resize((320,180),Image.Resampling.LANCZOS).save(RES/'drawable-xhdpi/tv_banner.png')
print('Generated vector icon, adaptive variants, SVG master, preview and TV banner.')
