#include <netinet/in.h>

#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>
#include <inttypes.h>

#define TS_HEADER_SIZE  (4)

#define TS_PACKET_SIZE  (188)
#define TS_EXTRA_SIZE (4)
#define NEW_TS_PACKET_SIZE (TS_PACKET_SIZE + TS_EXTRA_SIZE)
#define MAX_PID 8192
#define PES_PACKET_SIZE (3*1024*1024)

#define GET_PID(p)            (((((int)p[1]) & 0x1F) << 8) | p[2])
#define GET_START_UNIT(p)  ((p[1] & 0x40) >> 6)
#define GET_AF_LENGTH(p)      (p[4])

#define PES_HEADER_SIZE         (9)  //packet_start_code_prefix --- PES_header_data_length
#define AUDIO_STREAM_ID    (0xC0)
#define VIDEO_STREAM_ID    (0xE0)

void parsePesPacket(unsigned int tsPacketStartOffset, unsigned char* pPesPacket, unsigned int pesPacketSize) {
    unsigned int pes_header_size = 0;
    unsigned char* pEsPacket = NULL;
    unsigned int esPacketSize = 0;
    unsigned int checkBytes = 0;

    const char* picture_coding_type[8] = {
                    "forbidden",
                    "I-Frame",
                    "P-Frame",
                    "B-Frame",
                    "invalid",
                    "reserved",
                    "reserved",
                    "reserved",
    };

    fprintf(stderr, "tsPacketStartOffset=%d \n", tsPacketStartOffset);
    //fprintf(stderr, "packet_start_code_prefix=%02x-%02x-%02x-%02x \n", pPesPacket[0], pPesPacket[1], pPesPacket[2], pPesPacket[3]);

    if ((pPesPacket[0] == 0x00) && (pPesPacket[1] == 0x00) && (pPesPacket[2] == 0x01) && (pPesPacket[3] == VIDEO_STREAM_ID)) {
        pes_header_size = PES_HEADER_SIZE + pPesPacket[8];

        pEsPacket = pPesPacket + pes_header_size;
        esPacketSize = pesPacketSize -pes_header_size;
        checkBytes = 0;

        while (esPacketSize > checkBytes) {
            if (pEsPacket[0] == 0x00 && pEsPacket[1] == 0x00 && pEsPacket[2] == 0x01 && pEsPacket[3] == 0x00) { //Picture start header
                int pic_type = (pEsPacket[5] & 0x38) >> 3;
                fprintf(stderr, "[Picture start header] picture_coding_type:%s \n", picture_coding_type[pic_type]);
                break;
            } else if (pEsPacket[0] == 0x00 && pEsPacket[1] == 0x00 && pEsPacket[2] == 0x01 && pEsPacket[3] == 0xB3) { //Sequence video header
                fprintf(stderr, "[Sequence video header] \n");
                //continue to check
                checkBytes++;
                pEsPacket = pPesPacket + pes_header_size + checkBytes;
            } else {
                //may have null byte prefixing of start code and other start code cases
                checkBytes++;
                pEsPacket = pPesPacket + pes_header_size + checkBytes;
            }
        }
    }

    return;
}

int main(int argc, char *argv[])
{
    int byte_read;
    FILE* fd_ts;

    int ts_header_size;
    unsigned char temp;
    unsigned short payload_pid;
    unsigned char* pTsPacket;
    //unsigned int tsPacketSize;
    unsigned char* pCurrent;
    unsigned char* pPesPacket;
    //unsigned int pesPacketSize;
    unsigned int tsPacketOffset;
    unsigned int tsPacketStartOffset;
    int is_pes_start;
    unsigned int pes_len;

    if (argc == 3) {
        fd_ts = fopen(argv[1], "rb");
        if (fd_ts == NULL) {
            fprintf(stderr, "Can't find file %s\n", argv[1]);
            return 2;
        }

        payload_pid = atoi(argv[2]);
        if (payload_pid < 2 || payload_pid > MAX_PID-2) {
            fprintf(stderr, "Invalid PID, range is [2..8190]\n");
        }
    } else {
        fprintf(stderr, "Usage: 'ts2pes filename.ts payload_pid '\n");
        return 2;
    }

    //init ts buffer
    pTsPacket = malloc(NEW_TS_PACKET_SIZE);
    if (pTsPacket == NULL) {
        fprintf(stderr, "allocate TS packet failed: Out of memory\n");
        return 2;
    }
    //init pes buffer
    pPesPacket = malloc(PES_PACKET_SIZE);
    if (pPesPacket == NULL) {
        fprintf(stderr, "allocate PES packet failed: Out of memory\n");
        return 2;
    }

    /* Start to process the file */
    tsPacketOffset = 0;
    tsPacketStartOffset = 0;
    byte_read = 0;
    is_pes_start = 0;

    while(1) {
        tsPacketOffset += byte_read;

        /* read packets */
        byte_read = fread(pTsPacket, 1, NEW_TS_PACKET_SIZE, fd_ts);

        //fprintf(stderr, "{byte_read, tsPacketOffset}={%d, %d} \n", byte_read, tsPacketOffset);

        /* check packets pid */
        if (byte_read > 0) {
            pCurrent = pTsPacket + TS_EXTRA_SIZE;
            //fprintf(stderr, "{payload_pid, targetPid}={%d, %d} \n", payload_pid, GET_PID(pCurrent));


            if (payload_pid == GET_PID(pCurrent)) {
                ts_header_size = TS_HEADER_SIZE;

                /* check adaptation field */
                temp = (pCurrent[3] >> 4) & 0x03;
                if (temp == 0) {
                    ts_header_size = TS_PACKET_SIZE; // Reserved for future use by ISO/IEC
                    fprintf(stderr, "should not happened. Reserved for future use by ISO/IEC!! \n");
                } else if (temp == 1) {
                    ; //No adaptation_field, payload only
                    //fprintf(stderr, "No adaptation_field, payload only \n");
                } else if (temp == 2) {
                    ts_header_size = TS_PACKET_SIZE; //Adaptation_field only, no payload
                    //fprintf(stderr, "Adaptation_field only, no payload \n"); */
                } else if (temp == 3) {
                    ////Adaptation_field followed by payload
                    ts_header_size += GET_AF_LENGTH(pCurrent) + 1;
                }

                if ((is_pes_start == 0) && (1 == GET_START_UNIT(pCurrent))) {
                    is_pes_start = 1;
                    tsPacketStartOffset = tsPacketOffset;

                    pes_len = 0;
                    memcpy(pPesPacket + pes_len, pCurrent + ts_header_size, TS_PACKET_SIZE - ts_header_size);
                    pes_len += TS_PACKET_SIZE - ts_header_size;
                } else if ((is_pes_start == 1) && (0 == GET_START_UNIT(pCurrent))) {
                    memcpy(pPesPacket + pes_len, pCurrent + ts_header_size, TS_PACKET_SIZE - ts_header_size);
                    pes_len += TS_PACKET_SIZE - ts_header_size;
                } else if ((is_pes_start == 1) && (1 == GET_START_UNIT(pCurrent))) {
                    //a pes packet with new ES frame

                    //parse PES to ES and get picture type
                    if ((pPesPacket != NULL) && (pes_len > 0)) {
                        parsePesPacket(tsPacketStartOffset, pPesPacket, pes_len);
                    }

                    //reset variables
                    pes_len = 0;
                    tsPacketStartOffset = tsPacketOffset;

                    memcpy(pPesPacket + pes_len, pCurrent + ts_header_size, TS_PACKET_SIZE - ts_header_size);
                    pes_len += TS_PACKET_SIZE - ts_header_size;
                }
            }
        } else {
            break;
            fclose(fd_ts);
            fprintf(stderr, "Parse to file end!! \n");
        }
    }

    return 0;
}
