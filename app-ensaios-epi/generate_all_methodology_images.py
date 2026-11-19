import math
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

bold_f = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
reg_f = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

font_tab_val = ImageFont.truetype(bold_f, 52)
font_tab_unit = ImageFont.truetype(bold_f, 26)
font_tab_head = ImageFont.truetype(reg_f, 18)
font_tab_btn = ImageFont.truetype(bold_f, 22)

font_led = ImageFont.truetype(bold_f, 44)
font_led_lbl = ImageFont.truetype(bold_f, 15)
font_pnl_lbl = ImageFont.truetype(bold_f, 14)
font_switch = ImageFont.truetype(bold_f, 14)

def draw_hipot_unit(draw, origin_x, origin_y, scale=1.0, led_v="00.88", led_i="00.22", phone_curr="0,412 mA AC", status_green=True):
    """
    Renders the exact Hipot unit from the user's uploaded photo:
    - Yellow rugged Pelican/Storm case with open lid
    - Phone vertically mounted in lid showing '0,412 mA AC' on clean interface
    - Control panel with:
      * Top LED display: '00.88 kV' (High Voltage kV)
      * Bottom LED display: '00.22 A' (Low-voltage Current A)
      * Power switch ON/OFF with red flap
      * Center large fluted variac control knob with graduated dial
      * Multi-terminal test lead plugs (yellow, red, green, black)
      * Digital timer module with preset buttons
      * Overcurrent preset thumbwheel
      * Red emergency stop, Overcurrent / Timing toggle, Start/Stop buttons
      * Brand labels: VLUUKE and JVM Technology Co., Ltd
    """
    ox, oy = origin_x, origin_y
    
    # Yellow outer case base & lid
    # Base housing
    draw.polygon([
        (ox, oy + 420*scale), (ox + 720*scale, oy + 440*scale),
        (ox + 700*scale, oy + 880*scale), (ox + 10*scale, oy + 870*scale)
    ], fill=(245, 196, 22), outline=(195, 148, 10), width=int(5*scale))
    
    # Front latches and red handle
    draw.rounded_rectangle([ox + 220*scale, oy + 830*scale, ox + 490*scale, oy + 885*scale], radius=int(8*scale), fill=(235, 180, 15), outline=(180, 130, 10), width=int(2*scale))
    draw.rounded_rectangle([ox + 290*scale, oy + 845*scale, ox + 420*scale, oy + 885*scale], radius=int(6*scale), fill=(215, 45, 45)) # red handle grip
    for lx in [ox + 70*scale, ox + 170*scale, ox + 530*scale, ox + 630*scale]:
        draw.rectangle([lx, oy + 810*scale, lx + 26*scale, oy + 865*scale], fill=(225, 165, 15), outline=(170, 120, 10))

    # Open Yellow Lid (angled up)
    draw.polygon([
        (ox + 70*scale, oy), (ox + 650*scale, oy + 10*scale),
        (ox + 670*scale, oy + 430*scale), (ox + 50*scale, oy + 420*scale)
    ], fill=(248, 202, 26), outline=(200, 155, 12), width=int(5*scale))
    
    # Lid inner recess
    draw.polygon([
        (ox + 90*scale, oy + 25*scale), (ox + 630*scale, oy + 35*scale),
        (ox + 645*scale, oy + 410*scale), (ox + 75*scale, oy + 400*scale)
    ], fill=(238, 188, 18))

    # SMARTPHONE MOUNTED IN LID (as shown in user photo)
    ph_x, ph_y = ox + 140*scale, oy + 150*scale
    ph_w, ph_h = 100*scale, 200*scale
    # Phone body
    draw.rounded_rectangle([ph_x, ph_y, ph_x + ph_w, ph_y + ph_h], radius=int(8*scale), fill=(20, 22, 26), outline=(10, 12, 15), width=int(3*scale))
    # Phone screen (white UI)
    draw.rectangle([ph_x + 6*scale, ph_y + 8*scale, ph_x + ph_w - 6*scale, ph_y + ph_h - 8*scale], fill=(255, 255, 255))
    # Phone UI top bar & text
    draw.text((ph_x + 16*scale, ph_y + 35*scale), phone_curr, fill=(15, 23, 42), font=ImageFont.truetype(bold_f, max(10, int(15*scale))))
    # Start button in app
    draw.ellipse([ph_x + 35*scale, ph_y + 145*scale, ph_x + 65*scale, ph_y + 175*scale], fill=(248, 196, 25), outline=(205, 150, 10))

    # CONTROL PANEL SURFACE (Dark / Yellow framed metal faceplate)
    p_x1, p_y1 = ox + 25*scale, oy + 440*scale
    p_x2, p_y2 = ox + 685*scale, oy + 825*scale
    draw.rounded_rectangle([p_x1, p_y1, p_x2, p_y2], radius=int(12*scale), fill=(235, 228, 180), outline=(50, 55, 60), width=int(3*scale))
    draw.rounded_rectangle([p_x1 + 10*scale, p_y1 + 10*scale, p_x2 - 10*scale, p_y2 - 10*scale], radius=int(10*scale), fill=(28, 30, 35))

    # 1. TOP LED DISPLAY: High Voltage (kV)
    v_box = [ox + 50*scale, oy + 475*scale, ox + 225*scale, oy + 540*scale]
    draw.rectangle(v_box, fill=(10, 12, 15), outline=(70, 75, 85), width=int(2*scale))
    draw.text((ox + 65*scale, oy + 482*scale), led_v, fill=(255, 35, 40), font=ImageFont.truetype(bold_f, max(12, int(34*scale))))
    draw.text((ox + 185*scale, oy + 490*scale), "kV", fill=(255, 75, 80), font=ImageFont.truetype(bold_f, max(9, int(15*scale))))
    # Label plate below
    draw.rectangle([ox + 50*scale, oy + 545*scale, ox + 225*scale, oy + 565*scale], fill=(225, 175, 15))
    draw.text((ox + 60*scale, oy + 547*scale), "High Voltage (kV)", fill=(10, 15, 20), font=ImageFont.truetype(bold_f, max(8, int(11*scale))))

    # 2. BOTTOM LED DISPLAY: Low-voltage Current (A)
    i_box = [ox + 50*scale, oy + 580*scale, ox + 225*scale, oy + 645*scale]
    draw.rectangle(i_box, fill=(10, 12, 15), outline=(70, 75, 85), width=int(2*scale))
    draw.text((ox + 65*scale, oy + 587*scale), led_i, fill=(255, 35, 40), font=ImageFont.truetype(bold_f, max(12, int(34*scale))))
    draw.text((ox + 185*scale, oy + 595*scale), "A", fill=(255, 75, 80), font=ImageFont.truetype(bold_f, max(9, int(15*scale))))
    # Label plate below
    draw.rectangle([ox + 50*scale, oy + 650*scale, ox + 225*scale, oy + 670*scale], fill=(225, 175, 15))
    draw.text((ox + 55*scale, oy + 652*scale), "Low-voltage Current (A)", fill=(10, 15, 20), font=ImageFont.truetype(bold_f, max(7, int(10*scale))))

    # 3. POWER SWITCH WITH RED SAFETY FLAP
    draw.rectangle([ox + 65*scale, oy + 690*scale, ox + 165*scale, oy + 785*scale], fill=(245, 245, 245), outline=(130, 135, 140), width=int(2*scale))
    draw.rectangle([ox + 80*scale, oy + 710*scale, ox + 150*scale, oy + 765*scale], fill=(220, 35, 40), outline=(150, 20, 25), width=int(2*scale))
    draw.text((ox + 98*scale, oy + 692*scale), "ON", fill=(20, 25, 30), font=ImageFont.truetype(bold_f, max(7, int(11*scale))))
    draw.text((ox + 95*scale, oy + 768*scale), "OFF", fill=(20, 25, 30), font=ImageFont.truetype(bold_f, max(7, int(11*scale))))

    # 4. CENTER VARIAC KNOB & GRADUATED CIRCULAR DIAL
    k_cx, k_cy = ox + 380*scale, oy + 670*scale
    r_dial = 120*scale
    r_knob = 85*scale
    draw.ellipse([k_cx - r_dial, k_cy - r_dial, k_cx + r_dial, k_cy + r_dial], fill=(42, 45, 52), outline=(110, 115, 125), width=int(3*scale))
    draw.ellipse([k_cx - r_knob, k_cy - r_knob, k_cx + r_knob, k_cy + r_knob], fill=(16, 18, 22), outline=(70, 75, 85), width=int(4*scale))
    draw.ellipse([k_cx - 28*scale, k_cy - 45*scale, k_cx - 8*scale, k_cy - 25*scale], fill=(8, 10, 12)) # knob indent
    draw.text((k_cx - 40*scale, k_cy - 105*scale), "BYQ Hipot", fill=(200, 205, 215), font=ImageFont.truetype(bold_f, max(8, int(12*scale))))

    # 5. TEST TERMINALS & PLUGS ON TOP ROW
    # Sockets and plugged leads: Yellow (E Meter), Red (A Output), Green (PE), Black (Power)
    term_list = [
        (ox + 310*scale, oy + 465*scale, (245, 195, 20)), # yellow
        (ox + 350*scale, oy + 465*scale, (245, 195, 20)), # yellow
        (ox + 410*scale, oy + 465*scale, (220, 35, 40)),  # red
        (ox + 450*scale, oy + 465*scale, (220, 35, 40)),  # red
        (ox + 490*scale, oy + 465*scale, (34, 185, 75)),  # green
        (ox + 545*scale, oy + 465*scale, (20, 22, 26))    # black
    ]
    for tx, ty, tcol in term_list:
        draw.ellipse([tx - 10*scale, ty - 10*scale, tx + 10*scale, ty + 10*scale], fill=(15, 18, 20))
        draw.rectangle([tx - 7*scale, ty - 32*scale, tx + 7*scale, ty + 2*scale], fill=tcol, outline=(30, 30, 30), width=int(2*scale))
        # Cable curving upward
        draw.line([(tx, ty - 32*scale), (tx - 15*scale, ty - 85*scale)], fill=tcol, width=int(6*scale))

    # 6. RIGHT SIDE MODULES: Digital Timer, Presets & Pushbuttons
    # Digital Timer
    draw.rectangle([ox + 520*scale, oy + 510*scale, ox + 610*scale, oy + 575*scale], fill=(225, 230, 235), outline=(130, 135, 140), width=int(2*scale))
    draw.text((ox + 530*scale, oy + 580*scale), "Time Preset", fill=(210, 215, 225), font=ImageFont.truetype(reg_f, max(7, int(10*scale))))
    # Overcurrent thumbwheel
    draw.rectangle([ox + 535*scale, oy + 610*scale, ox + 600*scale, oy + 655*scale], fill=(120, 190, 220), outline=(80, 130, 160), width=int(2*scale))
    # Red Emergency Stop
    draw.ellipse([ox + 540*scale, oy + 695*scale, ox + 575*scale, oy + 730*scale], fill=(220, 35, 38), outline=(140, 20, 25), width=int(2*scale))
    # Pushbuttons: Start (green/yellow), Stop (red)
    draw.ellipse([ox + 530*scale, oy + 745*scale, ox + 555*scale, oy + 770*scale], fill=(245, 195, 20), outline=(170, 130, 10)) # Start
    draw.ellipse([ox + 590*scale, oy + 745*scale, ox + 615*scale, oy + 770*scale], fill=(220, 35, 38), outline=(140, 20, 25))  # Stop
    # Indicator Light (Green / Active)
    draw.ellipse([ox + 460*scale, oy + 745*scale, ox + 480*scale, oy + 765*scale], fill=(34, 197, 94) if status_green else (220, 35, 38))

    # 7. BUSINESS CARD & BRANDING PLATE
    # JVM Card
    draw.rectangle([ox + 340*scale, oy + 740*scale, ox + 450*scale, oy + 795*scale], fill=(255, 255, 255), outline=(170, 175, 185), width=int(1*scale))
    draw.text((ox + 375*scale, oy + 750*scale), "JVM", fill=(20, 50, 130), font=ImageFont.truetype(bold_f, max(8, int(13*scale))))
    draw.text((ox + 345*scale, oy + 770*scale), "joaonunes@jvmengenharia.com.br", fill=(40, 45, 55), font=ImageFont.truetype(reg_f, max(5, int(7*scale))))
    # VLUUKE Text
    draw.text((ox + 250*scale, oy + 760*scale), "VLUUKE", fill=(20, 25, 30), font=ImageFont.truetype(bold_f, max(8, int(13*scale))))

# =============================================================================
# 1. IMAGE 1: LUVAS ISOLANTES DE BORRACHA (NBR 16295 / ASTM D120)
# =============================================================================
print("Generating Luvas Isolantes Image...")
W, H = 1920, 1280
img_luvas = Image.new('RGB', (W, H), (255, 255, 255))
d = ImageDraw.Draw(img_luvas)

# Soft floor shadows
d.ellipse([100, 950, 1050, 1120], fill=(230, 235, 242))
d.ellipse([1050, 960, 1880, 1150], fill=(215, 220, 230))

# Left side: Transformer & Dielectric Water Tank Bench
# Transformer core
d.polygon([(60, 720), (220, 740), (230, 1020), (60, 1000)], fill=(88, 38, 32), outline=(50, 20, 16))
d.ellipse([100, 660, 170, 715], fill=(85, 36, 30))
d.rectangle([125, 620, 145, 660], fill=(220, 32, 36)) # HV red boot
# Transformer front plate
d.rectangle([90, 840, 160, 920], fill=(210, 215, 220), outline=(140, 145, 150))

# Black frame bench with aluminum top
d.polygon([(360, 600), (960, 580), (980, 660), (380, 690)], fill=(205, 212, 220), outline=(130, 138, 148), width=3)
d.polygon([(380, 690), (980, 660), (980, 680), (380, 710)], fill=(160, 168, 178))
# Bench frame legs
d.rectangle([280, 480, 320, 820], fill=(28, 30, 34))
d.rectangle([940, 460, 980, 780], fill=(28, 30, 34))
d.polygon([(260, 800), (480, 770), (480, 805), (260, 835)], fill=(25, 27, 30))
d.polygon([(880, 760), (1020, 740), (1020, 775), (880, 795)], fill=(25, 27, 30))

# Red Ribbed Insulators
def draw_insulator_col(cx, y1, y2, sheds=8, r=26):
    d.rectangle([cx - 8, y1, cx + 8, y2], fill=(130, 20, 25))
    step = (y2 - y1) / sheds
    for i in range(sheds):
        sy = y1 + i * step
        d.ellipse([cx - r, sy, cx + r, sy + 22], fill=(200, 35, 40), outline=(130, 15, 20), width=2)
        d.arc([cx - r + 3, sy + 2, cx + r - 3, sy + 16], start=200, end=340, fill=(250, 125, 130), width=2)

draw_insulator_col(270, 320, 500, sheds=7, r=24)
draw_insulator_col(970, 300, 480, sheds=7, r=24)
draw_insulator_col(350, 420, 620, sheds=7, r=24)

# Copper overhead busbar rod
d.line([(285, 380), (970, 320)], fill=(185, 120, 75), width=7)
d.line([(285, 378), (970, 318)], fill=(245, 185, 140), width=2)

# High voltage red cable from transformer to busbar
cable_pts = [(135, 620), (160, 480), (220, 340), (280, 360)]
for i in range(len(cable_pts) - 1):
    d.line([cable_pts[i], cable_pts[i+1]], fill=(220, 32, 38), width=16)

# Transparent Water Tank with 2 Submerged Lineman Gloves
tank_poly = [(520, 440), (840, 420), (870, 670), (570, 740)]
d.polygon(tank_poly, fill=(230, 244, 254))
water_poly = [(530, 520), (845, 495), (865, 665), (572, 730)]
d.polygon(water_poly, fill=(205, 232, 250), outline=(120, 180, 230), width=2)

# Gloves in water tank
def draw_tank_glove(gx, gy, w=65, l=175):
    # suspension hook
    d.line([(gx, gy - 65), (gx, gy - 15)], fill=(160, 165, 175), width=4)
    # spring clamps with red tips
    d.ellipse([gx - 55, gy - 35, gx - 20, gy + 8], fill=(25, 27, 30))
    d.polygon([(gx - 60, gy - 25), (gx - 85, gy - 10), (gx - 65, gy + 12)], fill=(220, 35, 38))
    d.ellipse([gx + 20, gy - 35, gx + 55, gy + 8], fill=(25, 27, 30))
    d.polygon([(gx + 60, gy - 25), (gx + 85, gy - 10), (gx + 65, gy + 12)], fill=(220, 35, 38))
    # glove bead and cuff
    d.ellipse([gx - w//2, gy - 6, gx + w//2, gy + 14], fill=(42, 45, 52), outline=(18, 20, 24), width=2)
    # body
    d.polygon([
        (gx - w//2, gy + 4), (gx + w//2, gy + 4),
        (gx + w//2 + 6, gy + 80), (gx + 15, gy + l),
        (gx - 15, gy + l), (gx - w//2 - 6, gy + 80)
    ], fill=(28, 30, 35), outline=(15, 17, 20), width=2)
    # fingers
    for fx in [-22, -7, 8, 22]:
        d.rounded_rectangle([gx + fx, gy + l - 10, gx + fx + 12, gy + l + 45], radius=5, fill=(24, 26, 30))

draw_tank_glove(610, 410, w=65, l=165)
draw_tank_glove(750, 390, w=65, l=165)
# Tank outline
d.polygon(tank_poly, outline=(140, 190, 230), width=5)

# Heavy black return cable to Hipot
d.line([(820, 480), (810, 720), (760, 920), (950, 1020), (1150, 980)], fill=(22, 24, 28), width=12)

# RIGHT SIDE: Exact Hipot unit from user photo
draw_hipot_unit(d, origin_x=1080, origin_y=220, scale=1.12, led_v="20.00", led_i="01.70", phone_curr="1,701 mA AC", status_green=True)

img_luvas.save("app/src/main/res/drawable/img_ensaio_luvas.jpg", "JPEG", quality=98)
print("Saved img_ensaio_luvas.jpg")

# =============================================================================
# 2. IMAGE 2: TAPETES E ESTRADOS ISOLANTES (ASTM D178 / NBR 14039)
# =============================================================================
print("Generating Tapetes Isolantes Image...")
img_tapetes = Image.new('RGB', (W, H), (255, 255, 255))
d = ImageDraw.Draw(img_tapetes)

# Floor shadows
d.ellipse([80, 960, 1080, 1140], fill=(230, 235, 242))
d.ellipse([1050, 960, 1880, 1150], fill=(215, 220, 230))

# Left side: Testing Bench for Rubber Mats with Upper & Lower Flat Electrodes
# Insulated Bench with Heavy Steel Table
d.polygon([(120, 680), (880, 620), (920, 730), (160, 800)], fill=(215, 222, 230), outline=(140, 148, 158), width=3)
d.polygon([(160, 800), (920, 730), (920, 760), (160, 830)], fill=(165, 172, 182))
# Bench Legs
d.rectangle([180, 790, 220, 1040], fill=(28, 30, 34))
d.rectangle([840, 720, 880, 970], fill=(28, 30, 34))
d.rectangle([140, 1030, 280, 1065], fill=(25, 27, 30))
d.rectangle([800, 960, 940, 995], fill=(25, 27, 30))

# LOWER GROUND ELECTRODE (Grounded aluminum/steel conductive plate)
d.polygon([(180, 670), (820, 620), (850, 695), (210, 750)], fill=(180, 190, 205), outline=(120, 130, 145), width=2)
# Ground cable from lower plate
d.line([(200, 740), (170, 870), (170, 1040)], fill=(34, 185, 75), width=8) # Green/yellow PE ground

# RUBBER DIELECTRIC MAT (Corrugated black/dark-grey insulating mat with clear edge margin)
mat_poly = [(220, 655), (780, 610), (810, 675), (250, 725)]
d.polygon(mat_poly, fill=(35, 38, 44), outline=(18, 20, 24), width=3)
# Surface ribbing/grooves on the dielectric mat
for my in range(635, 705, 7):
    d.line([(230 + (my - 635)*0.5, my), (790 + (my - 635)*0.5, my - 40)], fill=(55, 60, 70), width=2)

# Safety Edge Margin Indicator (Recuo Anti-Flashover Normativo)
d.polygon([(220, 655), (270, 651), (290, 721), (250, 725)], fill=(245, 195, 20, 120), outline=(210, 160, 15))
d.polygon([(740, 614), (780, 610), (810, 675), (770, 678)], fill=(245, 195, 20, 120), outline=(210, 160, 15))

# UPPER HIGH VOLTAGE MOVABLE ELECTRODE (Polished conductive aluminum plate)
top_el_poly = [(290, 640), (720, 605), (745, 655), (315, 695)]
d.polygon(top_el_poly, fill=(225, 235, 245), outline=(160, 175, 195), width=2)
# Metallic shine highlight
d.line([(310, 650), (715, 615)], fill=(255, 255, 255), width=3)

# Insulated High-Voltage Handle / Support arm for upper electrode
d.rectangle([505, 470, 535, 615], fill=(220, 35, 40), outline=(150, 20, 25), width=2) # Red insulated post
d.ellipse([495, 450, 545, 480], fill=(180, 20, 25))
# High Voltage cable from Hipot connected to top electrode
d.line([(520, 460), (520, 360), (750, 310), (1050, 350), (1150, 480)], fill=(220, 32, 38), width=14)
# High voltage warning label on test bench
d.rectangle([380, 755, 660, 785], fill=(245, 196, 22), outline=(180, 130, 10), width=2)
d.text((400, 762), "ENSAIO DE TAPETE ISOLANTE - ASTM D178 / NBR 14039", fill=(20, 25, 30), font=ImageFont.truetype(bold_f, 11))

# RIGHT SIDE: Exact Hipot unit from user photo
draw_hipot_unit(d, origin_x=1080, origin_y=220, scale=1.12, led_v="10.00", led_i="00.85", phone_curr="0,854 mA AC", status_green=True)

img_tapetes.save("app/src/main/res/drawable/img_ensaio_tapetes.jpg", "JPEG", quality=98)
print("Saved img_ensaio_tapetes.jpg")

# =============================================================================
# 3. IMAGE 3: FERRAMENTAS MANUAIS ISOLADAS 1000V (IEC 60900 / NBR 9699)
# =============================================================================
print("Generating Ferramentas Isoladas Image...")
img_ferramentas = Image.new('RGB', (W, H), (255, 255, 255))
d = ImageDraw.Draw(img_ferramentas)

# Floor shadows
d.ellipse([80, 960, 1080, 1140], fill=(230, 235, 242))
d.ellipse([1050, 960, 1880, 1150], fill=(215, 220, 230))

# Left side: Laboratory Water Bath for Hand Tools (Alicates, Chaves de Fenda 1000V)
# Bench
d.polygon([(140, 670), (860, 610), (900, 720), (180, 790)], fill=(215, 222, 230), outline=(140, 148, 158), width=3)
d.polygon([(180, 790), (900, 720), (900, 750), (180, 820)], fill=(165, 172, 182))
d.rectangle([200, 780, 240, 1030], fill=(28, 30, 34))
d.rectangle([820, 710, 860, 960], fill=(28, 30, 34))
d.rectangle([160, 1020, 300, 1055], fill=(25, 27, 30))
d.rectangle([780, 950, 920, 985], fill=(25, 27, 30))

# Stainless steel / transparent water bath tank
tank_f = [(260, 520), (780, 480), (810, 710), (290, 760)]
d.polygon(tank_f, fill=(235, 246, 255), outline=(130, 185, 225), width=4)
# Water level
d.polygon([(270, 580), (785, 545), (805, 705), (295, 750)], fill=(205, 232, 252), outline=(100, 170, 220), width=2)

# Tool suspension rack over water bath
d.line([(240, 440), (800, 395)], fill=(170, 175, 185), width=8) # Chrome support rail
d.rectangle([230, 430, 255, 620], fill=(40, 45, 50))
d.rectangle([785, 385, 810, 580], fill=(40, 45, 50))

# TOOL 1: 1000V Insulated Combination Pliers (Alicate Universal 1000V) suspended in water
# Steel jaws sticking out of water by exactly 24 mm (IEC 60900 safety margin)
pl_cx = 430
d.polygon([(pl_cx - 18, 390), (pl_cx + 18, 390), (pl_cx + 24, 490), (pl_cx - 24, 490)], fill=(195, 202, 212), outline=(110, 115, 125), width=2) # Steel Head
d.line([(pl_cx, 390), (pl_cx, 485)], fill=(90, 95, 105), width=2)
# Orange/Red 1000V double-layer insulating handles submerged in water
d.polygon([(pl_cx - 24, 490), (pl_cx - 40, 680), (pl_cx - 22, 680), (pl_cx - 8, 510)], fill=(235, 55, 35), outline=(160, 25, 15), width=2) # Left Handle
d.polygon([(pl_cx + 24, 490), (pl_cx + 40, 680), (pl_cx + 22, 680), (pl_cx + 8, 510)], fill=(235, 55, 35), outline=(160, 25, 15), width=2) # Right Handle
# Yellow inner insulation collar (1000V IEC symbol)
d.rectangle([pl_cx - 28, 485, pl_cx + 28, 500], fill=(245, 195, 20))
d.text((pl_cx - 16, 487), "1000V", fill=(20, 25, 30), font=ImageFont.truetype(bold_f, 9))

# TOOL 2: 1000V Insulated Screwdriver (Chave de Fenda Isolada 1000V)
sc_cx = 620
# Chrome-vanadium steel blade tip protruding (24±2 mm emerged)
d.rectangle([sc_cx - 5, 400, sc_cx + 5, 495], fill=(210, 215, 222), outline=(120, 125, 130), width=1)
# Red/Yellow ergonomic insulated shaft and handle submerged
d.rectangle([sc_cx - 8, 495, sc_cx + 8, 620], fill=(235, 55, 35), outline=(160, 25, 15), width=2) # Insulated shaft
d.rounded_rectangle([sc_cx - 22, 620, sc_cx + 22, 710], radius=8, fill=(235, 55, 35), outline=(160, 25, 15), width=2) # Handle
d.rounded_rectangle([sc_cx - 18, 635, sc_cx + 18, 695], radius=6, fill=(245, 195, 20)) # Yellow grip insert

# High voltage clip connected directly to metallic head/blade
d.rounded_rectangle([pl_cx - 12, 380, pl_cx + 12, 420], radius=4, fill=(220, 35, 40))
d.rounded_rectangle([sc_cx - 10, 395, sc_cx + 10, 430], radius=4, fill=(220, 35, 40))
# Red high voltage lead linking tools to Hipot
d.line([(pl_cx, 380), (sc_cx, 395)], fill=(220, 35, 40), width=6)
d.line([(sc_cx, 395), (820, 360), (1050, 380), (1150, 480)], fill=(220, 32, 38), width=14)

# Return ground cable from stainless steel bath to Hipot return
d.line([(780, 680), (840, 720), (950, 920), (1150, 980)], fill=(22, 24, 28), width=12)

# Safety notice banner on tank
d.rectangle([340, 770, 710, 800], fill=(245, 196, 22), outline=(180, 130, 10), width=2)
d.text((360, 777), "ENSAIO DIELÉTRICO 10 kV CA - IEC 60900 / NBR 9699", fill=(20, 25, 30), font=ImageFont.truetype(bold_f, 10))

# RIGHT SIDE: Exact Hipot unit from user photo
draw_hipot_unit(d, origin_x=1080, origin_y=220, scale=1.12, led_v="10.00", led_i="00.22", phone_curr="0,412 mA AC", status_green=True)

img_ferramentas.save("app/src/main/res/drawable/img_ensaio_ferramentas.jpg", "JPEG", quality=98)
print("Saved img_ensaio_ferramentas.jpg")

print("All methodology images generated successfully!")
