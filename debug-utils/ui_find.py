import re
import sys
import xml.etree.ElementTree as ET


def main():
    keyword = sys.argv[1]
    xml_path = sys.argv[2]
    tree = ET.parse(xml_path)
    found = 0
    for node in tree.iter('node'):
        text = node.get('text') or ''
        desc = node.get('content-desc') or ''
        if keyword in text or keyword in desc:
            bounds = node.get('bounds') or ''
            nums = re.findall(r'\d+', bounds)
            if len(nums) == 4:
                x1, y1, x2, y2 = map(int, nums)
                cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                print(f"center={cx},{cy} | text={text!r} | desc={desc!r}")
                found += 1
    if found == 0:
        print("NOT FOUND:", keyword)
        sys.exit(1)


main()
