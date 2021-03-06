#!/usr/bin/python

import h5py, numpy, os, datetime, csv, datetime
import moment


def get_info(fileinfo):
    valid = True

    # fails
    # 2017-05-18_16-31-S

    mea_no = -1
    mea_idx = -1
    try:
        mea_idx = fileinfo.index("MEA") + 1
        if mea_idx:
            mea_no = fileinfo[mea_idx]
    except:
        valid = False

    extra = ""
    if valid:
        for info in range(mea_idx + 1, len(fileinfo)):
            extra = extra + str(fileinfo[info]) + " "

    # print mea_no
    # print extra

    if valid:
        return "MEA:" + mea_no + "\nComment:" + extra

    else:
        return "Invalid. Info: " + str(fileinfo)



def process_directories(currentdir, info):
    files = os.listdir(currentdir)
    dirs = [elem for elem in files if os.path.isdir(os.path.join(currentdir, elem))]

    # print "Processing directory: " + currentdir

    if not dirs:
        # print "process directories parsing " + currentdir
        parsedir(currentdir, info)

    for dirname in dirs:
        nextdir = os.path.join(currentdir, dirname)
        process_directories(os.path.join(currentdir, dirname), info + "/" + dirname)
        parsedir(currentdir, info)


def parsedir(directory, dir_info):

    # print "parsing dir: " + directory

    for filename in os.listdir(directory):

        # print "\tparsing file " + filename

        # Check that we have a hdf5 file
        extension = filename.split('.')[-1]
        if not "h5" in extension:
            return

        try:
            infile = h5py.File(os.path.join(directory,filename),"r")
        except Exception as e:
            print "cant parse file or smth"
            print os.path.join(directory,filename)
            print e
            return

        # Get information about the file
        fileinfo = filename.split(' ')
        fileinfo[-1] = fileinfo[-1].split('.')[0]
        datestring = fileinfo[0]
        if "MEA" in datestring:
            datestring = datestring.split("MEA")[0]
            fileinfo[0] = "MEA"

        # Get date info
        try:
            m = moment.date(datestring, '%Y-%m-%d_%H-%M-%S')
            # print m.weekday
            # print m.format('YYYY-M-D_H-m')
            try:
                dirname = os.path.join(directory, m.format('YYYY-M-D_H-m-s'))
                if not os.path.exists(dirname):
                    outdir = os.makedirs(dirname)
            except Exception as e:
                print "creating dir failed"
                print e

        except Exception as e:
            print "something went wrong with date parse" + fileinfo[0]
            print datestring
            print e
            return

        # write the info file
        info_string = get_info(fileinfo)
        with open(("/home/peteraa/MEAdata/metadata/" + datestring + ".txt"), "w") as f:
            print "writing info " + info_string
            f.write(info_string)
            print "writing dir " + info_string
            f.write(dir_info)


        segLength = 1000

        channelData = infile["Data"]["Recording_0"]["AnalogStream"]["Stream_0"]["ChannelData"]
        with open(("/home/peteraa/MEAdata/" + datestring + ".txt"), "w") as f:
            writer = csv.writer(f)

            print "len is {0}".format(len(channelData[0]))
            print "we need {0} iters ".format(len(channelData[0])/segLength)

            elemsPerRow = segLength
            myArray = numpy.zeros(len(channelData[0])*60)
            rowsNeeded = len(myArray)/elemsPerRow

            print "we have {0} elements".format(len(channelData[0])*60)
            print "we want {1} elements per equating {0} rows".format(rowsNeeded, elemsPerRow)

            for channelNo in range (0, 60):
                print "adding channel no {0}".format(channelNo)
                seg = channelData[channelNo]
                for segment in range (0, (len(channelData[0])/segLength) - 1):
                    for i in range (0, segLength):
                        myArray[i + (60*segment + channelNo)] = seg[i + segment*segLength]

            asRows = numpy.array_split(myArray, rowsNeeded)
            print(len(asRows))
            print(len(asRows[0]))

            print "here we go yolo"
            writer.writerows(asRows)

            exit(0)

def main():
    files = process_directories("/home/peteraa/Fuckton_of_MEA_data/hdf5_15", "")

main()
