package ilda;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;



/**
 * This class reads a file and passes the data to frames and points.
 *
 * ilda files are explained <a href=http://www.ilda.com/resources/StandardsDocs/ILDA_IDTF14_rev011.pdf>here</a>.
 * Here's a quick breakdown:<br>
 *     <ul>
 * <li>ILDA V0 is 3D and uses palettes</li>
 * <li>ILDA V1 is 2D and uses palettes</li>
 * <li>ILDA V2 is a palette</li>
 * <li>ILDA V3 is a 24-bit palette, but was discontinued and is not a part of the official standard anymore</li>
 * <li>ILDA V4 is 3D with true-colour information in BGR format</li>
 * <li>ILDA V5 is 2D with true-colour information in BGR format</li>
 * </ul>
 *
 * An ilda file is composed of headers that always start with "ILDA", followed by three zeros and the version number.
 * A complete header is 32 bytes.
 * After the header, the data follows. In case of a palette (V2), each data point has three bytes: R, G and B.
 * In case of a frame (V0/1/4/5), the X, Y and Z (for 3D frames) values are spread out over two bytes
 * Then either two status bytes follow with a blanking bit and palette colour number, or BGR values.
 */
public class IldaReader  extends FileParser
{

    //protected ArrayList<Integer> framePositions = new ArrayList<Integer>();
    public IldaPalette palette;

    public IldaReader(String location) throws FileNotFoundException
    {
        super(location);

        if (b == null)
        {
            throw new FileNotFoundException("Error: could not read file at " + location);
        }

    }

    public IldaReader(File file) throws FileNotFoundException
    {
        this(file.getAbsolutePath());
    }

    /**
     * Parse an ilda file from disk
     * Normally only this static method should be required to retrieve all IldaFrames from a file
     * @param location path to the ilda file
     * @return list of all loaded frames
     */

    public static ArrayList<IldaFrame> readFile(String location) throws FileNotFoundException
    {
        IldaReader reader = new IldaReader(location);
        return reader.getFramesFromBytes();
    }



    public void setPalette(IldaPalette palette) {
        this.palette = palette;
    }

    private ArrayList<IldaFrame> getFramesFromBytes()
    {
        reset();
        ArrayList<IldaFrame> theFrames = new ArrayList<IldaFrame>();
        if (b == null) {
            //This should have been caught before
            return null;
        }

        if (b.length < 32) {
            //There isn't even a complete header here!
            throw new RuntimeException("Error: file is not long enough to be a valid ILDA file!");
        }

        //Check if the first four bytes read ILDA:


        String hdr = parseString(4);
        if (!hdr.equals("ILDA")) {
            throw new RuntimeException("Error: invalid ILDA file, found " + hdr + ", expected ILDA instead");
        }

        reset();

        loadIldaFrame( theFrames);
        return theFrames;


    }

    /**
     * Iterative method to load ilda frames, the new frames are appended to an ArrayList.
     * @param f IldaFrame ArrayList where the new frame will be appended
     */

    private void loadIldaFrame(  ArrayList<IldaFrame> f)
    {
        if (position >= b.length - 32)
        {
            return;        //no complete header
        }



        //Bytes 0-3: ILDA
        String hdr = parseString(4);
        if (!hdr.equals("ILDA"))
        {
            return;
        }

        //Bytes 4-6: Reserved
        skip(3);

        //Byte 7: format code
        int ildaVersion = parseByte();

        //Bytes 8-15: frame name
        String name = parseString(8);

        //Bytes 16-23: company name
        String company = parseString(8);

        //Bytes 24-25: point count
        int pointCount = parseShort();

        //Bytes 26-27: frame number in frames or palette number in palettes
        int frameNumber = parseShort();

        //Bytes 28-29: total frames
        skip(2);

        //Byte 30: projector number
        int scannerhead = parseByte() & 0xff;

        //Byte 31: Reserved
        skip(1);




        if (ildaVersion == 2) {

            palette = new IldaPalette();

            palette.name = name;
            palette.companyName = company;
            palette.totalColors = pointCount;

            //Byte 30: scanner head.
            palette.scannerHead = scannerhead;


            // ILDA V2: Palette information

            for (int i = 0; i < pointCount; i ++) {
                palette.addColour(parseByte(), parseByte(), parseByte());
            }
        } else {
            IldaFrame frame = new IldaFrame();

            frame.setIldaFormat(ildaVersion);
            frame.setFrameName(name);
            frame.setCompanyName(company);
            frame.setFrameNumber(frameNumber);
            frame.setPalette(ildaVersion == 0 || ildaVersion == 1);

            boolean is3D = ildaVersion == 0 || ildaVersion == 4;


            for (int i = 0; i < pointCount; i++)
            {
                float x = parseShort();
                float y = parseShort();
                float z = 0;
                if (is3D) z = parseShort();
                boolean bl = false;
                if ((parseByte() & 0x40) == 64) bl = true;
                if (ildaVersion == 0 || ildaVersion == 1) {
                    IldaPoint point = new IldaPoint(x * 0.00003051757f, y * -0.00003051757f, z * 0.00003051757f, parseByte() & 0xff, bl);
                    frame.addPoint(point);
                } else if (ildaVersion == 4 || ildaVersion == 5) {
                    int blue = parseByte();
                    int g = parseByte();
                    int r = parseByte();
                    IldaPoint point = new IldaPoint(x * 0.00003051757f, y * -0.00003051757f, z * 0.00003051757f, blue & 0xff, g & 0xff, r & 0xff, bl);
                    frame.addPoint(point);
                }


            }

            if (frame.isPalette()) {
                if (palette == null) {
                    palette = new IldaPalette();
                    palette.setDefaultPalette();
                }

                frame.palettePaint(palette);
            }
            f.add(frame);

            loadIldaFrame( f);
        }
    }


}
