
#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <algorithm>

class BitStreamParser 
{
public:
    BitStreamParser() {
        mBitPos = 0;
        mBufferLen = 0;
        mbitsMask[] = {0x0, 0x1, 0x3, 0x7, 0xF, 0x1F, 0x3F, 0x7F, 0xFF};
    }
    
    //[TBD]
    //maybe need a endian parameter for generic usage
    //currently, is for BIGENDIAN case
    BitStreamParser(uint8_t *data, uint32_t dataLen = 0);
    virtual ~BitStreamParser() {mBuffer = nullptr;}

    //return value of nBits, and shit nBits
    uint32_t readBits(uint32_t nBits);
    
    //return value of nBits, but not to shit nBits
    uint32_t showBits(uint32_t nBits);
    
    //just shit nBits
    bool skipBits(uint32_t nBits);
   
private:
    uint8_t *mBuffer; //buffer point
    uint32_t mBufferLen; //buffer len

    uint32_t mBitPos; //the starting bit of next read
    uint32_t mbitsMask;
};

BitStreamParser::BitStreamParser(uint8_t *data, uint32_t dataLen)
    : BitStreamParser()
{
    mBuffer = data;
    
    if (dataLen > 0) {
        mBufferLen =dataLen;
    }
}

uint32_t BitStreamParser::readBits(uint32_t nBits)
{
    //assert if nBits > 32
    assert(nBits <= 32);

    //check with mBufferLen
    //assert((mBitPos + nBits) <= (mBufferLen << 3));

    //calculate byte position from mBitPos
    uint32_t bytePos = mBitPos >> 3;
    
    //read current byte from bytePos
    uint8_t byte = mBuffer[bytePos];
    
    //calculate bits not read at current byte
    uint32_t bitsLeft = 8 - (mBitPos & 7);
    
    //calculate bits can read at current byte
    uint32_t bitsReadNum = std::min(bitsLeft, nBits);
    
    uint32_t value = (byte >> (bitsLeft - bitsReadNum)) & mbitsMask[bitsReadNum];

    //update mBitPos after read
    mBitPos += bitsReadNum;

    if (nBits > bitsReadNum) {
        uint32_t bitsRemains = nBits - bitsReadNum;
        return (value << bitsRemains) | readBits(bitsRemains);
    } else {
        return value;
    }
}

uint32_t BitStreamParser::showBits(uint32_t nBits)
{
    uint32_t value = readBits(nBits);
    //update mBitPos
    mBitPos -= nBits;
    
    return value;
}

bool BitStreamParser::skipBits(uint32_t nBits)
{
    //check with mBufferLen
    //assert((mBitPos + nBits) <= (mBufferLen << 3));
    
    //update mBitPos
    mBitPos += nBits;
    
    return true;
}