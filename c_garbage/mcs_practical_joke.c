#include<stdio.h>
#include<stdlib.h>
#include<math.h>


void UploadSine(int Channel, int Amplitude, int Period, int Repeats, int Stepsize);
void AddLoop(int Channel, int Vectors, int Repeats);
void SetSegment(int Channel, int Segment);
void ClearChannel(int Channel);
int AddDataPoint(int Channel, int duration, int value);

#define WRITE_REGISTER(at, address, value) printf("[line - %d]: Wrote value %#010x to address %#010x\n", at, value, address);

  void UploadSine(int Channel, int Amplitude, int Period, int Repeats, int Stepsize) {

    int yold = 0;
    int duration = 0;
    int datapoints = 0;
    volatile int i;
    int y;
    int vectors_used;

    vectors_used = 0;

    ClearChannel(Channel);
    for (i = 0; i < Period; i++)
      {
        y = Amplitude * sin((((double)i)/Period)*2*3.1415);
        //		y = -(Amplitude *i)/Period;

        if (abs(y - yold) > Stepsize)
          {
            vectors_used += AddDataPoint(Channel, duration, yold+0x8000);
            datapoints++;
            yold = y;
            duration = 1; // 20 us
          }
        else
          {
            duration++;
          }
      }
    vectors_used += AddDataPoint(Channel, duration, yold+0x8000);
    AddLoop(Channel, vectors_used, Repeats);

    // Create Sideband Information
    printf("side band confusion inc\n");
    ClearChannel(Channel+1);
    printf("cleared channel\n");
    vectors_used = 0;
    vectors_used += AddDataPoint(Channel+1, Period, 0x0019);
    printf("added datapoint\n");
    AddLoop(Channel+1, vectors_used, Repeats);
    printf("did the last thingy\n");
    //	AddDataPoint(Channel+1, 10, 0x0009); // keep Electrode connected to ground after stimulation

    /* println */
  }

  void AddLoop(int Channel, int Vectors, int Repeats)
  {
    int ChannelReg;
    int LoopVector;

    ChannelReg = 0x9f20 + Channel*4;

    if (Repeats > 1)
      {
        LoopVector = 0x10000000 | (Repeats << 16) | Vectors;
        WRITE_REGISTER(__LINE__, ChannelReg, LoopVector);
      }
  }

  void SetSegment(int Channel, int Segment)
  {
    int SegmentReg = 0x9200 + Channel*0x20;
    WRITE_REGISTER(__LINE__, SegmentReg, Segment);  // Any write to this register clears the Channeldata
  }



  void ClearChannel(int Channel)
  {
    int ClearReg = 0x920c + Channel*0x20;
    WRITE_REGISTER(__LINE__, ClearReg, 0);      // Any write to this register clears the Channeldata
  }

  int AddDataPoint(int Channel, int duration, int value)
  {
    int vectors_used = 0;
    int	Vector;
    int ChannelReg = 0x9f20 + Channel*4;

    if (duration > 1000)
      {
        Vector = 0x04000000 | (((duration / 1000) - 1) << 16) | (value & 0xffff);
        WRITE_REGISTER(__LINE__, ChannelReg, Vector);  // Write Datapoint to STG Memory
        duration %= 1000;
        vectors_used++;
      }

    if (duration > 0)
      {
        Vector = ((duration - 1) << 16) | (value & 0xffff);
        WRITE_REGISTER(__LINE__, ChannelReg, Vector);  // Write Datapoint to STG Memory
        vectors_used++;
      }

    return vectors_used;
  }

int main(int argc, char** argv) {
  UploadSine(0, 200, 1000, 1, 1);
}
