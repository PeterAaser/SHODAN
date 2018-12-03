package cyborg

/**
  In germany they count ...differently
 */
object mcsChannelMap {


  /**
    Maps the visual layout of MCS channels to the data stream
    For instance, to get what MCS labels as channel 21 we need to
    pick out the 23rd element in the datastream
    */
  val MCStoSHODAN = Map(
                12 -> 20,   13 -> 18,   14 -> 15,   15 -> 14,   16 -> 11,   17 -> 9,
    21 -> 23,   22 -> 21,   23 -> 19,   24 -> 16,   25 -> 13,   26 -> 10,   27 -> 8,    28 -> 6,
    31 -> 25,   32 -> 24,   33 -> 22,   34 -> 17,   35 -> 12,   36 -> 7,    37 -> 5,    38 -> 4,
    41 -> 28,   42 -> 29,   43 -> 27,   44 -> 26,   45 -> 3,    46 -> 2,    47 -> 0,    48 -> 1,
    51 -> 31,   52 -> 30,   53 -> 32,   54 -> 33,   55 -> 56,   56 -> 57,   57 -> 59,   58 -> 58,
    61 -> 34,   62 -> 35,   63 -> 37,   64 -> 42,   65 -> 47,   66 -> 52,   67 -> 54,   68 -> 55,
    71 -> 36,   72 -> 38,   73 -> 40,   74 -> 43,   75 -> 46,   76 -> 49,   77 -> 51,   78 -> 53,
                82 -> 39,   83 -> 41,   84 -> 44,   85 -> 45,   86 -> 48,   87 -> 50
  )


  /**
    A map between channel visualizations on SHODAN and the corresponding MCS channel
    Channel 0 for SHODAN corresponds to channel 21 in the MCS viz
    */
  val SHODANvizToMCS = Map(
                0  -> 21,   1  -> 31,   2  -> 41,   3  -> 51,   4  -> 61,   5  -> 71,
    6  -> 12,   7  -> 22,   8  -> 32,   9  -> 42,   10 -> 52,   11 -> 62,   12 -> 72,   13 -> 82,
    14 -> 13,   15 -> 23,   16 -> 33,   17 -> 43,   18 -> 53,   19 -> 63,   20 -> 73,   21 -> 83,
    22 -> 14,   23 -> 24,   24 -> 34,   25 -> 44,   26 -> 54,   27 -> 64,   28 -> 74,   29 -> 84,
    30 -> 15,   31 -> 25,   32 -> 35,   33 -> 45,   34 -> 55,   35 -> 65,   36 -> 75,   37 -> 85,
    38 -> 16,   39 -> 26,   40 -> 36,   41 -> 46,   42 -> 56,   43 -> 66,   44 -> 76,   45 -> 86,
    46 -> 17,   47 -> 27,   48 -> 37,   49 -> 47,   50 -> 57,   51 -> 67,   52 -> 77,   53 -> 87,
                54 -> 28,   55 -> 38,   56 -> 48,   57 -> 58,   58 -> 68,   59 -> 78
  )


  /**
    As with everything else, the mapping between the stim channel register on the DSP and the
    actual electrode is nonsensical and arbitrary.

    Say you want to stimulate electrode 2, 4 and 12, then the corresponding MCS channels are
    23, 28 and 38.

    Fun fact, there is a pattern here, but for the fourth quadrant that pattern is reversed.
    Incredible...
    */
  val getMCSstimChannel = Map(
                  0  -> 23,   1  -> 25,   2  -> 28,   3  -> 31,   4  -> 34,   5  -> 36,
      6  -> 20,   7  -> 21,   8  -> 24,   9  -> 29,   10 -> 30,   11 -> 35,   12 -> 38,   13 -> 39,
      14 -> 18,   15 -> 19,   16 -> 22,   17 -> 27,   18 -> 32,   19 -> 37,   20 -> 40,   21 -> 41,
      25 -> 26,   26 -> 33,   27 -> 42,   28 -> 43,   29 -> 44,   22 -> 15,   23 -> 16,   24 -> 17,
      38 -> 11,   39 -> 10,   40 -> 7,    41 -> 2,    42 -> 57,   43 -> 52,   44 -> 49,   45 -> 48,
      46 -> 9,    47 -> 8,    48 -> 5,    49 -> 0,    50 -> 59,   51 -> 54,   52 -> 51,   53 -> 50,
      30 -> 14,   31 -> 13,   32 -> 12,   33 -> 3,    34 -> 56,   35 -> 47,   36 -> 46,   37 -> 45,
                  54 -> 6,    55 -> 4,    56 -> 1,    57 -> 58,   58 -> 55,   59 -> 53,
  )


  /**
    Gets the corresponding segment offset for a SHODAN channel. For SHODAN channel 13, the corresponding
    MCS channel is 82, which in turn has offset 39 in the datastream
    */
  val getMCSchannel = SHODANvizToMCS.toList.map{ case(idx, channel) => (idx, MCStoSHODAN(channel)) }.toMap
}
