#!/usr/bin/env python3
"""Use a compact phone framebuffer for WebView fullscreen CI on hosted runners."""
import os
from pathlib import Path
avd_home=Path(os.environ.get('ANDROID_AVD_HOME',str(Path.home()/'.android/avd')))
config=avd_home/'test.avd/config.ini'
if not config.is_file():
    raise SystemExit(f'CI AVD config not found: {config}')
updates={'hw.lcd.width':'720','hw.lcd.height':'1280','hw.lcd.density':'320','showDeviceFrame':'no'}
result=[]
for line in config.read_text().splitlines():
    key=line.split('=',1)[0].strip()
    if key in updates:result.append(f'{key}={updates.pop(key)}')
    else:result.append(line)
result.extend(f'{key}={value}' for key,value in updates.items())
config.write_text('\n'.join(result)+'\n')
print('CI phone framebuffer: 720x1280, density 320.')
