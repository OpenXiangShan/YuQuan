def gen_sequential(num, start):
    addr = start & 0xffffffff
    for i in range(num):
        print('W ' + hex(addr << 6))
        addr = (addr + 1) & 0xffffffff

if __name__ == '__main__':
    gen_sequential(500000, 4694500)


