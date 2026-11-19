import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1920, 1080  # Full HD 16:9 widescreen format
img = Image.new('RGB', (W, H), (255, 255, 255))
draw = ImageDraw.Draw(img)

# Fonts
font_path_bold = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
font_path_reg = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

f_head = ImageFont.truetype(font_path_bold, 32)
f_subhead = ImageFont.truetype(font_path_reg, 20)
f_badge = ImageFont.truetype(font_path_bold, 17)
f_panel_title = ImageFont.truetype(font_path_bold, 22)
f_panel_desc = ImageFont.truetype(font_path_reg, 16)
f_disp_big = ImageFont.truetype(font_path_bold, 44)
f_disp_unit = ImageFont.truetype(font_path_bold, 22)
f_tab_val = ImageFont.truetype(font_path_bold, 48)
f_tab_unit = ImageFont.truetype(font_path_bold, 20)
f_label = ImageFont.truetype(font_path_bold, 15)
f_micro = ImageFont.truetype(font_path_bold, 13)

# 1. Subtle studio floor ground shadow
for y in range(860, H):
    ratio = (y - 860) / (H - 860)
    shadow_val = int(255 - 8 * math.sin(ratio * math.pi / 2))
    draw.line([(0, y), (W, y)], fill=(shadow_val, shadow_val, shadow_val))

# TOP HEADER BANNER
draw.rounded_rectangle([40, 25, W - 40, 115], radius=10, fill=(248, 250, 252), outline=(203, 213, 225), width=2)
draw.rounded_rectangle([40, 25, 52, 115], radius=2, fill=(2, 132, 199))

draw.text((70, 36), "REGISTRO FOTOGRÁFICO DO ENSAIO DIELÉTRICO REAL EM LABORATÓRIO", fill=(15, 23, 42), font=f_head)
draw.text((70, 78), "Metodologia executiva conforme ABNT NBR 16295:2014 e ASTM D120 • Tensão de prova 20,0 kV • Monitoramento de fuga", fill=(71, 85, 105), font=f_subhead)

draw.rounded_rectangle([W - 460, 38, W - 60, 102], radius=8, fill=(238, 242, 255), outline=(165, 180, 252), width=1)
draw.text((W - 440, 48), "EQUIPAMENTO: LUVAS CLASSE 2", fill=(67, 56, 202), font=f_badge)
draw.text((W - 440, 73), "Tensão: 20 kV CA | Fuga: ≤ 14-16 mA", fill=(99, 102, 241), font=f_label)

# =========================================================================
# LEFT FRAME: PAINEL A - CUBA ACRÍLICA & LUVAS SUBMERSAS (MONTAGEM DO ENSAIO)
# =========================================================================
f1_x1, f1_y1, f1_x2, f1_y2 = 40, 135, 960, 990
# Soft shadow behind card
draw.rounded_rectangle([f1_x1 + 4, f1_y1 + 4, f1_x2 + 4, f1_y2 + 4], radius=12, fill=(230, 234, 240))
# Card body
draw.rounded_rectangle([f1_x1, f1_y1, f1_x2, f1_y2], radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
# Card Header
draw.rounded_rectangle([f1_x1, f1_y1, f1_x2, f1_y1 + 48], radius=10, fill=(15, 45, 74))
draw.rectangle([f1_x1, f1_y1 + 25, f1_x2, f1_y1 + 48], fill=(15, 45, 74))
draw.text((f1_x1 + 20, f1_y1 + 14), "PAINEL A: MONTAGEM DA CUBA DIELÉTRICA E LUVAS SUBMERSAS", fill=(255, 255, 255), font=f_panel_title)

# Bench Base in Panel A
bench_left, bench_top, bench_right, bench_bot = f1_x1 + 30, 770, f1_x2 - 30, 810
draw.ellipse([bench_left - 20, bench_bot - 10, bench_right + 20, bench_bot + 35], fill=(225, 230, 238))
draw.rectangle([bench_left, bench_top, bench_right, bench_bot], fill=(70, 80, 92), outline=(45, 52, 60), width=2)
# Bench legs
draw.rectangle([bench_left + 40, bench_bot, bench_left + 75, bench_bot + 130], fill=(45, 52, 60))
draw.rectangle([bench_right - 75, bench_bot, bench_right - 40, bench_bot + 130], fill=(45, 52, 60))
draw.rectangle([(bench_left + bench_right)//2 - 18, bench_bot, (bench_left + bench_right)//2 + 18, bench_bot + 130], fill=(45, 52, 60))
# Rubber foot pads
for fx in [bench_left + 35, bench_right - 80, (bench_left + bench_right)//2 - 23]:
    draw.rectangle([fx, bench_bot + 125, fx + 45, bench_bot + 135], fill=(20, 20, 20))

# Left High Voltage Red Ribbed Insulator Column
ins1_x1, ins1_x2 = f1_x1 + 60, f1_x1 + 130
ins1_top, ins1_bot = 320, bench_top
draw.rectangle([ins1_x1 - 10, ins1_bot - 18, ins1_x2 + 10, ins1_bot], fill=(40, 40, 45))
# Sheds (flanged skirts)
for y in range(ins1_top + 45, ins1_bot - 20, 36):
    # Cast shadow under skirt
    draw.ellipse([ins1_x1 - 20, y + 18, ins1_x2 + 20, y + 36], fill=(130, 15, 20))
    # Skirt body
    draw.ellipse([ins1_x1 - 18, y, ins1_x2 + 18, y + 30], fill=(195, 30, 35), outline=(135, 15, 20), width=2)
    # Gloss highlight
    draw.arc([ins1_x1 - 12, y + 2, ins1_x2 + 12, y + 18], start=190, end=350, fill=(245, 110, 115), width=2)

# Right High Voltage Red Ribbed Insulator Column
ins2_x1, ins2_x2 = f1_x2 - 130, f1_x2 - 60
draw.rectangle([ins2_x1 - 10, ins1_bot - 18, ins2_x2 + 10, ins1_bot], fill=(40, 40, 45))
for y in range(ins1_top + 45, ins1_bot - 20, 36):
    draw.ellipse([ins2_x1 - 20, y + 18, ins2_x2 + 20, y + 36], fill=(130, 15, 20))
    draw.ellipse([ins2_x1 - 18, y, ins2_x2 + 18, y + 30], fill=(195, 30, 35), outline=(135, 15, 20), width=2)
    draw.arc([ins2_x1 - 12, y + 2, ins2_x2 + 12, y + 18], start=190, end=350, fill=(245, 110, 115), width=2)

# Solid Copper Busbar rod spanning between insulators
bar_y = 330
draw.line([(f1_x1 + 80, bar_y), (f1_x2 - 80, bar_y)], fill=(190, 105, 35), width=12)
draw.line([(f1_x1 + 80, bar_y - 2), (f1_x2 - 80, bar_y - 2)], fill=(245, 175, 95), width=3) # Metallic sheen
draw.ellipse([f1_x1 + 75, bar_y - 8, f1_x1 + 95, bar_y + 8], fill=(170, 90, 25))
draw.ellipse([f1_x2 - 95, bar_y - 8, f1_x2 - 75, bar_y + 8], fill=(170, 90, 25))

# TRANSPARENT ACRYLIC WATER TANK
tank_x1, tank_y1, tank_x2, tank_y2 = f1_x1 + 175, 410, f1_x2 - 175, bench_top
# Drop shadow under tank
draw.ellipse([tank_x1 + 20, tank_y2 - 12, tank_x2 - 20, tank_y2 + 22], fill=(215, 225, 235))
# Acrylic box body (very light cyan-blue tint with dark edge borders)
draw.rectangle([tank_x1, tank_y1, tank_x2, tank_y2], fill=(238, 248, 255), outline=(148, 185, 215), width=4)
# Top acrylic rim
draw.rectangle([tank_x1 - 8, tank_y1 - 6, tank_x2 + 8, tank_y1 + 14], fill=(225, 242, 255), outline=(130, 170, 205), width=3)

# Water body inside tank
water_surf_y = 485
draw.rectangle([tank_x1 + 4, water_surf_y, tank_x2 - 4, tank_y2 - 4], fill=(205, 234, 252))
# Water meniscus surface line
draw.line([(tank_x1 + 4, water_surf_y), (tank_x2 - 4, water_surf_y)], fill=(90, 170, 230), width=5)
# Depth gradients & light refractions
for wy in range(water_surf_y + 30, tank_y2 - 30, 45):
    draw.line([(tank_x1 + 20, wy), (tank_x1 + 120, wy)], fill=(235, 247, 255), width=2)
    draw.line([(tank_x2 - 140, wy + 15), (tank_x2 - 30, wy + 15)], fill=(235, 247, 255), width=2)

# FUNCTION TO DRAW REALISTIC DIELECTRIC BLACK RUBBER GLOVE
def draw_realistic_glove(cx):
    # Suspended top clamp from busbar
    draw.line([(cx, bar_y), (cx, 385)], fill=(180, 185, 190), width=6)
    draw.rectangle([cx - 45, 385, cx + 45, 420], fill=(20, 22, 25))
    # Spring clamp handles (red & black)
    draw.polygon([(cx - 55, 395), (cx - 25, 395), (cx - 42, 425)], fill=(220, 30, 30))
    draw.polygon([(cx + 25, 395), (cx + 55, 395), (cx + 42, 425)], fill=(220, 30, 30))

    # Glove cuff bead ring (rolled edge of dielectric glove)
    draw.ellipse([cx - 48, 410, cx + 48, 434], fill=(45, 48, 54), outline=(18, 20, 24), width=3)

    # Glove Gauntlet / Arm body (Black natural rubber with realistic matte gradient)
    glove_poly = [
        (cx - 46, 424), (cx + 46, 424),
        (cx + 52, 510), (cx + 56, 610), (cx + 48, 690),
        (cx + 22, 720), (cx - 22, 720),
        (cx - 48, 690), (cx - 56, 610), (cx - 52, 510)
    ]
    draw.polygon(glove_poly, fill=(28, 30, 34), outline=(15, 17, 20), width=2)

    # 5 Fingers submerged in water
    fingers = [
        (-38, 14, 46),  # Thumb
        (-18, 14, 58),  # Index
        (2, 14, 62),    # Middle
        (22, 14, 56),   # Ring
        (40, 13, 44)    # Pinky
    ]
    for fx, fw, fh in fingers:
        draw.rounded_rectangle([cx + fx, 712, cx + fx + fw, 712 + fh], radius=6, fill=(24, 26, 30), outline=(15, 17, 20), width=2)
        # Finger tip highlight
        draw.arc([cx + fx + 2, 712 + fh - 10, cx + fx + fw - 2, 712 + fh], start=0, end=180, fill=(65, 70, 80), width=2)

    # Matte rubber curvature highlight streak
    draw.line([(cx - 26, 440), (cx - 30, 680)], fill=(70, 76, 88), width=4)
    draw.line([(cx + 26, 440), (cx + 30, 680)], fill=(55, 60, 70), width=2)

    # Stainless steel electrode chain dropping down inside the glove
    for cy in range(bar_y + 10, 680, 18):
        draw.ellipse([cx - 4, cy, cx + 4, cy + 14], outline=(200, 205, 215), width=2)

# Draw Two Gloves (Class 2 pair)
draw_realistic_glove(tank_x1 + 130)
draw_realistic_glove(tank_x2 - 130)

# High voltage connecting cable (Red heavy silicone cable)
draw.line([(f1_x1 + 80, bar_y), (f1_x1 + 30, bar_y + 80), (f1_x1 + 20, 650)], fill=(220, 35, 40), width=8)
draw.line([(f1_x1 + 80, bar_y - 1), (f1_x1 + 30, bar_y + 79), (f1_x1 + 20, 649)], fill=(255, 110, 115), width=2)

# Green grounding wire attached to tank ground lug
draw.line([(tank_x2 - 10, tank_y2 - 20), (bench_right - 60, bench_top + 10)], fill=(34, 160, 60), width=5)

# Panel A Footer Callout
draw.rounded_rectangle([f1_x1 + 20, f1_y2 - 60, f1_x2 - 20, f1_y2 - 15], radius=6, fill=(240, 249, 255), outline=(186, 230, 253), width=1)
draw.text((f1_x1 + 35, f1_y2 - 50), "• Cuba em acrílico com água potável e recuo de borda seca conforme NBR 16295", fill=(3, 105, 161), font=f_label)
draw.text((f1_x1 + 35, f1_y2 - 32), "• Eletrodos de corrente interna suspensos e interligados à barra de alta tensão (20 kV)", fill=(71, 85, 105), font=f_micro)

# =========================================================================
# RIGHT FRAME: PAINEL B - MALETA HIPOT BYQ5kVA & TABLET COM LEITURA DIGITAL
# =========================================================================
f2_x1, f2_y1, f2_x2, f2_y2 = 985, 135, W - 40, 990
# Soft shadow behind card
draw.rounded_rectangle([f2_x1 + 4, f2_y1 + 4, f2_x2 + 4, f2_y2 + 4], radius=12, fill=(230, 234, 240))
# Card body
draw.rounded_rectangle([f2_x1, f2_y1, f2_x2, f2_y2], radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
# Card Header
draw.rounded_rectangle([f2_x1, f2_y1, f2_x2, f2_y1 + 48], radius=10, fill=(15, 45, 74))
draw.rectangle([f2_x1, f2_y1 + 25, f2_x2, f2_y1 + 48], fill=(15, 45, 74))
draw.text((f2_x1 + 20, f2_y1 + 14), "PAINEL B: MALETA HIPOT BYQ5kVA & LEITURA DIGITAL FLUKE 369FC", fill=(255, 255, 255), font=f_panel_title)

# YELLOW RUGGED CASE (MALETA AMARELA PELICAN / STORM)
case_x1, case_y1, case_x2, case_y2 = f2_x1 + 30, 200, f2_x2 - 30, 920

# Upper Lid Shell (Yellow Rugged Case open upright)
lid_y1, lid_y2 = case_y1, 480
draw.rounded_rectangle([case_x1, lid_y1, case_x2, lid_y2], radius=16, fill=(245, 185, 20), outline=(200, 145, 10), width=4)
# Interior recessed lid area
draw.rounded_rectangle([case_x1 + 15, lid_y1 + 15, case_x2 - 15, lid_y2 - 15], radius=10, fill=(230, 170, 15))

# TABLET MOUNTED INSIDE THE LID (RUNNING BLE MULTIMETER APP)
tab_w, tab_h = 420, 230
tab_cx = (case_x1 + case_x2) // 2
tab_x1, tab_y1 = tab_cx - tab_w // 2, lid_y1 + 20
tab_x2, tab_y2 = tab_x1 + tab_w, tab_y1 + tab_h

# Tablet rugged black casing
draw.rounded_rectangle([tab_x1, tab_y1, tab_x2, tab_y2], radius=10, fill=(20, 22, 26), outline=(10, 12, 15), width=3)
# Tablet screen (High resolution active interface)
draw.rounded_rectangle([tab_x1 + 10, tab_y1 + 10, tab_x2 - 10, tab_y2 - 10], radius=5, fill=(255, 255, 255))

# Top Yellow Header Bar on Tablet
draw.rectangle([tab_x1 + 10, tab_y1 + 10, tab_x2 - 10, tab_y1 + 45], fill=(245, 185, 25))
draw.text((tab_x1 + 22, tab_y1 + 18), "369FC - BLE CONECTADO", fill=(15, 23, 42), font=f_badge)
draw.ellipse([tab_x2 - 38, tab_y1 + 22, tab_x2 - 24, tab_y1 + 36], fill=(34, 197, 94)) # Green BLE dot

# Live Measurement Digits (1,701 mA AC)
draw.text((tab_x1 + 35, tab_y1 + 75), "1,701", fill=(15, 23, 42), font=f_tab_val)
draw.text((tab_x1 + 205, tab_y1 + 92), "mA AC", fill=(71, 85, 105), font=f_tab_unit)

# Live mini graph waveform
draw.rectangle([tab_x1 + 35, tab_y1 + 145, tab_x2 - 35, tab_y1 + 175], fill=(248, 250, 252), outline=(226, 232, 240), width=1)
graph_pts = [(tab_x1 + 45 + i * 25, tab_y1 + 160 + int(7 * math.sin(i * 0.9))) for i in range(13)]
for i in range(len(graph_pts) - 1):
    draw.line([graph_pts[i], graph_pts[i + 1]], fill=(2, 132, 199), width=2)

# Action button on tablet
draw.rounded_rectangle([tab_x1 + 35, tab_y1 + 185, tab_x2 - 35, tab_y1 + 218], radius=6, fill=(245, 185, 25))
draw.text((tab_x1 + 130, tab_y1 + 193), "✓ Salvar Leitura de Fuga", fill=(15, 23, 42), font=f_label)

# LOWER MAIN CONSOLE SHELL (YELLOW HOUSING)
draw.rounded_rectangle([case_x1, 465, case_x2, case_y2], radius=18, fill=(245, 185, 20), outline=(200, 145, 10), width=5)
# Corner bumpers
for bx, by in [(case_x1, 465), (case_x2 - 28, 465), (case_x1, case_y2 - 28), (case_x2 - 28, case_y2 - 28)]:
    draw.rounded_rectangle([bx, by, bx + 28, by + 28], radius=6, fill=(35, 38, 42))

# DARK GRAPHITE CONTROL PANEL FACEPLATE
p_x1, p_y1, p_x2, p_y2 = case_x1 + 20, 490, case_x2 - 20, case_y2 - 20
draw.rounded_rectangle([p_x1, p_y1, p_x2, p_y2], radius=10, fill=(30, 32, 36), outline=(50, 54, 62), width=3)

# PANEL BRAND & MODEL HEADING
draw.text((p_x1 + 25, p_y1 + 18), "BYQ5kVA Hipot Tester", fill=(245, 185, 25), font=f_panel_title)
draw.text((p_x1 + 25, p_y1 + 48), "HIGH VOLTAGE DIELECTRIC TEST SET • 50 kV / 5 kVA", fill=(156, 163, 175), font=f_micro)

# DIGITAL DISPLAY 1: HIGH VOLTAGE (kV)
d1_x1, d1_y1, d1_x2, d1_y2 = p_x1 + 25, p_y1 + 75, p_x1 + 270, p_y1 + 175
draw.rectangle([d1_x1, d1_y1, d1_x2, d1_y2], fill=(12, 14, 18), outline=(65, 70, 80), width=2)
# Glowing red 7-segment digital font
draw.text((d1_x1 + 20, d1_y1 + 18), "20.25", fill=(255, 40, 45), font=f_disp_big)
draw.text((d1_x1 + 175, d1_y1 + 32), "kV", fill=(255, 90, 90), font=f_disp_unit)
draw.text((d1_x1 + 20, d1_y1 + 68), "High Voltage (kV)", fill=(203, 213, 225), font=f_label)

# DIGITAL DISPLAY 2: LOW-VOLTAGE CURRENT (A)
d2_x1, d2_y1, d2_x2, d2_y2 = p_x1 + 25, p_y1 + 195, p_x1 + 270, p_y1 + 295
draw.rectangle([d2_x1, d2_y1, d2_x2, d2_y2], fill=(12, 14, 18), outline=(65, 70, 80), width=2)
draw.text((d2_x1 + 20, d2_y1 + 18), "00.52", fill=(255, 40, 45), font=f_disp_big)
draw.text((d2_x1 + 175, d2_y1 + 32), "A", fill=(255, 90, 90), font=f_disp_unit)
draw.text((d2_x1 + 20, d2_y1 + 68), "Low-voltage Current (A)", fill=(203, 213, 225), font=f_label)

# CENTRAL ROTARY VARIAC VOLTAGE ADJUSTER KNOB
knob_cx, knob_cy = p_x1 + 435, p_y1 + 195
# Graduated Dial Ring (Concentric metallic ring with tick marks)
draw.ellipse([knob_cx - 95, knob_cy - 95, knob_cx + 95, knob_cy + 95], fill=(42, 46, 52), outline=(100, 105, 115), width=2)
for deg, txt in [(220, "0"), (180, "50"), (140, "75"), (90, "150"), (40, "200"), (0, "250")]:
    rad = math.radians(deg)
    tx = knob_cx + int(76 * math.cos(rad)) - 10
    ty = knob_cy - int(76 * math.sin(rad)) - 10
    draw.text((tx, ty), txt, fill=(209, 213, 219), font=f_micro)

# Central ribbed knob with indicator notch
draw.ellipse([knob_cx - 60, knob_cy - 60, knob_cx + 60, knob_cy + 60], fill=(20, 22, 25), outline=(60, 65, 75), width=3)
draw.line([(knob_cx, knob_cy), (knob_cx + 28, knob_cy - 36)], fill=(245, 185, 25), width=5) # Pointer

# TIMERS & SAFETY MODULES (RIGHT SIDE)
# Time preset digital module
draw.rectangle([p_x2 - 180, p_y1 + 80, p_x2 - 30, p_y1 + 160], fill=(226, 232, 240), outline=(100, 116, 139), width=2)
draw.text((p_x2 - 155, p_y1 + 90), "01:00", fill=(15, 23, 42), font=f_panel_title)
draw.text((p_x2 - 165, p_y1 + 128), "Time Preset (min)", fill=(51, 65, 85), font=f_micro)

# Overcurrent protection module
draw.rectangle([p_x2 - 180, p_y1 + 180, p_x2 - 30, p_y1 + 260], fill=(220, 252, 231), outline=(34, 197, 94), width=2)
draw.text((p_x2 - 155, p_y1 + 190), "10.0 A", fill=(21, 128, 61), font=f_panel_title)
draw.text((p_x2 - 165, p_y1 + 228), "Overcurrent Set", fill=(22, 101, 52), font=f_micro)

# EMERGENCY STOP MUSHROOM BUTTON
draw.ellipse([p_x2 - 140, p_y1 + 295, p_x2 - 70, p_y1 + 365], fill=(220, 38, 38), outline=(153, 27, 27), width=4)
draw.ellipse([p_x2 - 128, p_y1 + 307, p_x2 - 82, p_y1 + 353], fill=(248, 113, 113))

# TERMINALS & CONNECTION CABLES
draw.ellipse([p_x1 + 310, p_y1 + 325, p_x1 + 345, p_y1 + 360], fill=(220, 38, 38), outline=(153, 27, 27), width=2) # Red HV
draw.ellipse([p_x1 + 365, p_y1 + 325, p_x1 + 400, p_y1 + 360], fill=(234, 179, 8), outline=(161, 98, 7), width=2)   # Yellow
draw.ellipse([p_x1 + 420, p_y1 + 325, p_x1 + 455, p_y1 + 360], fill=(234, 179, 8), outline=(161, 98, 7), width=2)   # Yellow
draw.ellipse([p_x1 + 475, p_y1 + 325, p_x1 + 510, p_y1 + 360], fill=(34, 197, 94), outline=(21, 128, 61), width=2)  # Green PE

draw.text((p_x1 + 320, p_y1 + 365), "HV", fill=(239, 68, 68), font=f_micro)
draw.text((p_x1 + 372, p_y1 + 365), "Meter", fill=(234, 179, 8), font=f_micro)
draw.text((p_x1 + 485, p_y1 + 365), "PE", fill=(34, 197, 94), font=f_micro)

# Panel B Footer Callout
draw.rounded_rectangle([f2_x1 + 20, f2_y2 - 60, f2_x2 - 20, f2_y2 - 15], radius=6, fill=(254, 249, 195), outline=(253, 224, 71), width=1)
draw.text((f2_x1 + 35, f2_y2 - 50), "• Tensão controlada via Variac e monitorada em tempo real (20.25 kV CA RMS)", fill=(161, 98, 7), font=f_label)
draw.text((f2_x1 + 35, f2_y2 - 32), "• Comunicação BLE c/ Fluke 369FC registrando corrente de fuga de 1,701 mA", fill=(71, 85, 105), font=f_micro)

# =========================================================================
# BOTTOM SUMMARY FOOTER ACROSS ENTIRE FRAME
# =========================================================================
draw.rounded_rectangle([40, H - 75, W - 40, H - 20], radius=8, fill=(241, 245, 249), outline=(226, 232, 240), width=1)
draw.text((65, H - 56), "CONJUNTO DE ENSAIO REAL CALIBRADO: Bancada de alta tensão c/ cuba acrílica e isoladores pedestal • Maleta Hipot Tester BYQ5kVA 50kV • Fluke 369FC BLE", fill=(51, 65, 85), font=f_label)

# Save to destination
output_path = 'app/src/main/res/drawable/img_ensaio_luvas.jpg'
img.save(output_path, 'JPEG', quality=96)
print(f'Successfully generated high-realism technical photographic composite at {output_path} with size {os.path.getsize(output_path)} bytes')
