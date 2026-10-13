"""热力图逐格取色核对（列已确定，补行检测 + 分类比对）。"""
from PIL import Image

img = Image.open(r'C:/Users/Administrator/Documents/StatsApp/debug-shots/140-heat-category.png').convert('RGB')
px = img.load()

# 15 列中心（由上一步列检测得到：段起 208，格宽 46，间距 57）
col_centers = [208 + 23 + i * 57 for i in range(15)]

# 行检测：在网格 x 范围 200..1055 内投影
X0, X1, Y0, Y1 = 200, 1056, 1850, 2280
def is_cell_pixel(r, g, b):
    for tr, tg, tb in [(0x4A,0x6C,0xF7),(0xF5,0x9E,0x0B),(0x34,0xA8,0x53)]:
        if (r-tr)**2+(g-tg)**2+(b-tb)**2 < 3600:
            return True
    return r < 242 and g < 242 and b < 248

row_count = [0]*(Y1-Y0)
for y in range(Y0, Y1):
    for x in range(X0, X1):
        if is_cell_pixel(*px[x, y]):
            row_count[y-Y0] += 1

segs, start = [], None
for i, c in enumerate(row_count + [0]):
    if c >= 300 and start is None: start = i
    elif c < 300 and start is not None:
        segs.append((start+Y0, i-1+Y0)); start = None
row_centers = [(s+e)//2 for s, e in segs]
print('行段:', segs)
print('行中心:', row_centers)

def classify(r, g, b):
    best, bd = None, 10**9
    for name, (tr, tg, tb) in {'蓝': (0x4A,0x6C,0xF7), '橙': (0xF5,0x9E,0x0B), '绿': (0x34,0xA8,0x53)}.items():
        d = (r-tr)**2+(g-tg)**2+(b-tb)**2
        if d < bd: bd, best = d, name
    return best if bd < 3600 else None

print('\n实际热力图矩阵（列=周，行=周一→周日）：')
matrix = []
for cy in row_centers:
    row = []
    for cx in col_centers:
        rs = gs = bs = 0
        for dy in (-4, 0, 4):
            for dx in (-4, 0, 4):
                r, g, b = px[cx+dx, cy+dy]; rs += r; gs += g; bs += b
        row.append(classify(rs//9, gs//9, bs//9) or '空')
    matrix.append(row)
    print(' '.join(row))

# 预期比对：最后两列
expect = {
    (0, 13): '蓝', (1, 13): '蓝', (2, 13): '蓝', (3, 13): '橙', (4, 13): '橙', (5, 13): '蓝', (6, 13): '绿',
    (0, 14): '蓝', (1, 14): '蓝', (2, 14): '蓝', (3, 14): '蓝', (4, 14): '橙', (5, 14): '蓝', (6, 14): '空',
}
print('\n逐格核对（倒数第2列=9/28-10/4，倒数第1列=10/5-10/11）：')
ok = True
weekday = '一二三四五六日'
for (row, col), exp in sorted(expect.items(), key=lambda kv: (kv[0][1], kv[0][0])):
    act = matrix[row][col]
    flag = '✓' if act == exp else '✗'
    if act != exp: ok = False
    print(f'{flag} {col-14:+d}列 周{weekday[row]}  实际={act} 预期={exp}')
print('\n结论:', '全部吻合 ✓' if ok else '存在不一致 ✗')
