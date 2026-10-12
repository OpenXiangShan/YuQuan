import random

def gen_random(num, start):
    addr = start & 0xffffffff
    for i in range(num):
        pre=random.choice(['W ','R '])
        print(pre + hex(addr << 6))
        addr = random.randint(4694501, 5194501) & 0xffffffff

if __name__ == '__main__':
    gen_random(1000000, 4694500)


