package com.arappor.veppalar;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.color.Color;
import com.itextpdf.kernel.color.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.border.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.VerticalAlignment;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;


public class ArapporVepalarCandidateCardCreator {

    private static String FONT_SIZE;
    private static String FONT_COLOR;
    private static String INPUT_FILE;
    private static String PATH_TO_CANDIDATE_PHOTOS;
    private static String PATH_TO_PARTY_SYMBOLS;
    private static String PATH_TO_PDF;
    private static String PATH_TO_TAMIZH_FONT;

    private static String TAMIZH_FONT_NAME;
    private static Font TAMIZH_FONT;

    private static Color TEXT_COLOR = new DeviceRgb(189, 48, 56);
    private static Color BACKGROUND_COLOR = new DeviceRgb(253, 235, 235);
    public static final float TEXT_LINE_GAP_RATIO = 1.25f;

    public static void main(String args[]) {

        init();

        try {
            beginProcess();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.err.println(e);
        } finally {
            System.out.println("Task complete....");
        }
    }

    private static void init() {
        Properties prop = new Properties();
        InputStream input = null;

        try {

            input = new FileInputStream("config.properties");

            // load a properties file
            prop.load(input);

            FONT_SIZE = prop.getProperty("FONT_SIZE");
            INPUT_FILE = prop.getProperty("INPUT_FILE");
            PATH_TO_CANDIDATE_PHOTOS = prop.getProperty("PATH_TO_CANDIDATE_PHOTOS");
            PATH_TO_PARTY_SYMBOLS = prop.getProperty("PATH_TO_PARTY_SYMBOLS");
            PATH_TO_PDF = prop.getProperty("PATH_TO_PDF");
            PATH_TO_TAMIZH_FONT = prop.getProperty("PATH_TO_TAMIZH_FONT");

            if(PATH_TO_TAMIZH_FONT != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, new File(PATH_TO_TAMIZH_FONT));
                System.out.printf("Font Details:\nName = %s\nFont Name = %s\nFamily Name = %s\nPostscript Name = %s\n",
                        f.getName(), f.getFontName(), f.getFamily(), f.getPSName());
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                TAMIZH_FONT_NAME = f.getFontName();
                TAMIZH_FONT = f;
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (FontFormatException e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void beginProcess() throws IOException {
        Scanner scanner = new Scanner(new File(INPUT_FILE));
        System.out.printf("Reading file: %s\n", INPUT_FILE);
        boolean first = true;
        boolean skipHeader = true;
        while (scanner.hasNextLine()) {
            String[] col = scanner.nextLine().split(",");
            if (first) {
                first = false;
                if (skipHeader) {
                    continue;
                }
            }
            String constituencyID = col[0];
            String candidateName = col[2];
            String partyName = col[3];

            System.out.printf("%s|%s|%s\n", constituencyID, candidateName, partyName);

            createImage(constituencyID, candidateName, partyName);
            createPDF(constituencyID, candidateName, partyName);
        }
        scanner.close();
		
		/*Scanner scanner = new Scanner(new File(INPUT_FILE));
        scanner.useDelimiter(",");
        while(scanner.hasNext()){
        	String constituencyID = scanner.next();
        	String candidateName = scanner.next();
        	String partyName = scanner.next();
        	
        	createPDF(constituencyID, candidateName, partyName);
        }*/

    }

    public static boolean fitsWithin(String str, FontMetrics fm, Graphics2D g, int textStartX, int maxWidth) {
        Rectangle2D rect = fm.getStringBounds(str, g);
        return (rect.getWidth() + textStartX) <= maxWidth;
    }

    public static String[] chunkString(String str, int length) {
        str = str.trim();
        String[] chunks = new String[(int) Math.ceil((1.0 * str.length()) / length)];
        for (int i = 0; i < chunks.length; i++) {
            int beginIndex = i * length;
            int endIndex = ((i + 1) * length) > str.length() ? str.length() : ((i + 1) * length);
            chunks[i] = str.substring(i * length, endIndex);
        }
        return chunks;
    }

    /**
     * Wrap given string based on the maximum width available
     *
     * @param str
     * @param fm
     * @param g
     * @param textStartX
     * @param maxWidth
     * @return
     */
    public static String[] wrapString(String str, FontMetrics fm, Graphics2D g, int textStartX, int maxWidth) {
        if (!fitsWithin(str, fm, g, textStartX, maxWidth)) {
            String[] nameParts = str.split(" ");
            if (nameParts.length < 2) {
                // Can't do anything
                System.out.printf("WARN: Could not split as there were no spaces in string: %s\n", str);
                return chunkString(nameParts[0], 20);
            }
            ArrayList<String> sList = new ArrayList<>();
            String stringSoFar = "";
            String prevString = "";
            for (int curIdx = 0; curIdx < nameParts.length; curIdx++) {
                stringSoFar = (prevString + " " + nameParts[curIdx]).trim();
                if (fitsWithin(stringSoFar, fm, g, textStartX, maxWidth)) {
                    prevString = stringSoFar;
                } else {
                    prevString = prevString.trim();
                    if (!prevString.isEmpty()) {
                        sList.add(prevString);
                    }
                    prevString = nameParts[curIdx];
                }
            }
            prevString = prevString.trim();
            if (!prevString.isEmpty()) {
                sList.add(prevString);
            }
            return sList.toArray(new String[]{});
        } else {
            return new String[]{str};
        }
    }

    public static String guessFilename(String constituencyID, String dir) throws FileNotFoundException {
        System.out.printf("Guessing image filename: constituency_id = %s | dir = %s\n", constituencyID, dir);
        Pattern pattern = Pattern.compile("([A-Z]+)([0-9]+)");
        Matcher m = pattern.matcher(constituencyID);
        boolean found = m.find();
        if (!found) {
            throw new IllegalArgumentException("Invalid constituency-id format: " + constituencyID);
        }
        String constituency = m.group(1);
        String number = m.group(2);

        String[] formats = {
                "%s%s.JPG", "%s_%s.JPG",
                "%s%s.jpg", "%s_%s.jpg",
        };

        return Arrays.stream(formats)
                .map(s -> String.format(s, constituency, number))
                .map(s -> Paths.get(dir, s))
                .filter(f -> Files.exists(f))
                .map(Path::toString).findFirst().orElseThrow(FileNotFoundException::new);
    }

    public static void createImage(String constituencyID, String candidateName, String partyName) throws IOException {
        String dest = Paths.get(PATH_TO_PDF, constituencyID + ".JPG").toString();

        String candidatePhotoImageFile = guessFilename(constituencyID, PATH_TO_CANDIDATE_PHOTOS);
        String partySymbolImageFile = guessFilename(constituencyID, PATH_TO_PARTY_SYMBOLS);

        System.out.println("Destination = " + dest);
        System.out.println("Candidate Photo = " + candidatePhotoImageFile);
        System.out.println("Party Symbol = " + partySymbolImageFile);

        java.awt.Image candidatePhoto = ImageIO.read(new File(candidatePhotoImageFile))
                .getScaledInstance(220, -240, BufferedImage.SCALE_SMOOTH);
        java.awt.Image partySymbol = ImageIO.read(new File(partySymbolImageFile))
                .getScaledInstance(150, -150, BufferedImage.SCALE_SMOOTH);

        int targetWidth = 631;
        int targetHeight = 561;

        java.awt.Color bg = java.awt.Color.WHITE; //new java.awt.Color(253, 235, 235);
        java.awt.Color fg = new java.awt.Color(189, 48, 56);

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

        g.setBackground(bg);
        g.setColor(bg);
        g.fillRect(0, 0, targetWidth, targetHeight);

        g.setColor(fg);

        int candidateNameFontSize = 10 +
                (candidateName.length() > 30 ?
                        Integer.parseInt(FONT_SIZE) - 2 :
                        candidateName.length() > 20 ?
                                Integer.parseInt(FONT_SIZE) - 1 :
                                Integer.parseInt(FONT_SIZE));

        Font candidateNameFont = null;
        if(TAMIZH_FONT != null) {
            candidateNameFont = TAMIZH_FONT; //new Font(TAMIZH_FONT_NAME, Font.BOLD, candidateNameFontSize);
        } else {
            candidateNameFont = new Font("Arial", Font.BOLD, candidateNameFontSize);
        }
        g.setFont(candidateNameFont);//Font.getFont("Helvetica"));
        FontMetrics fm = g.getFontMetrics();
        double textHeight = (int) fm.getStringBounds(candidateName, g).getHeight();
        System.out.printf("\tCandidate Text Height: %f\n", textHeight);
        int textStartX = 50;
        int textStartY = 90;
        String[] candidateNameParts = wrapString(candidateName, fm, g, textStartX, targetWidth);
        int curY = textStartY;
        for (int i = 0; i < candidateNameParts.length; i++) {
            // g.drawLine(0, curY, targetWidth, curY);
            g.drawString(candidateNameParts[i], textStartX, curY);
            curY += (int) (TEXT_LINE_GAP_RATIO * textHeight);
            // g.drawLine(0, curY, targetWidth, curY);
        }

        int partyNameFontSize = 10 +
                (partyName.length() > 30 ?
                        Integer.parseInt(FONT_SIZE) - 8 :
                        partyName.length() > 20 ?
                                Integer.parseInt(FONT_SIZE) - 6 :
                                Integer.parseInt(FONT_SIZE) - 3);
        g.setFont(new Font("Arial", Font.BOLD, partyNameFontSize));//Font.getFont("Helvetica"));
        fm = g.getFontMetrics();
        textHeight = (int) fm.getStringBounds(partyName, g).getHeight();
        System.out.printf("\tParty Text Height: %f\n", textHeight);
        String[] partyNameParts = wrapString(partyName, fm, g, textStartX, targetWidth);
        for (int i = 0; i < partyNameParts.length; i++) {
            // g.drawLine(0, curY, targetWidth, curY);
            g.drawString(partyNameParts[i], textStartX, curY);
            curY += (int) (TEXT_LINE_GAP_RATIO * textHeight);
            // g.drawLine(0, curY, targetWidth, curY);
        }

        int photoTop = curY - (int) textHeight;
        int photoCentreY = photoTop + candidatePhoto.getHeight(null) / 2;
        // g.drawLine(0, photoTop, targetWidth, photoTop);
        // g.drawLine(0, photoCentreY, targetWidth, photoCentreY);

        int photoCentreX = 160;
        photoCentreY = (photoCentreY > 300) ? photoCentreY : 300;
        // g.drawLine(0, photoCentreY, targetWidth, photoCentreY);
        g.drawImage(
                candidatePhoto,
                photoCentreX - candidatePhoto.getWidth(null) / 2,
                photoCentreY - candidatePhoto.getHeight(null) / 2,
                null);

        int symbolCentreX = 405;
        int symbolCentreY = photoCentreY;
        g.drawImage(
                partySymbol,
                symbolCentreX - partySymbol.getWidth(null) / 2,
                symbolCentreY - partySymbol.getHeight(null) / 2,
                null);
        g.dispose();

        // Simple write to jpg
        //ImageIO.write(result, "jpg", new File(dest));

        // Complex write to jpg, as we want to tweak the quality
        Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = (ImageWriter) iter.next();
        ImageWriteParam iwp = writer.getDefaultWriteParam();

        iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        iwp.setCompressionQuality(1.0f); // Set the quality between 0 (lowest) to 1 (highest)

        FileImageOutputStream fios = new FileImageOutputStream(new File(dest));
        writer.setOutput(fios);
        IIOImage image = new IIOImage(result, null, null);
        writer.write(null, image, iwp);
        writer.dispose();
    }

    private static void createPDF(String constituencyID, String candidateName, String partyName) throws FileNotFoundException, MalformedURLException {
        // Creating a PdfWriter object
        String dest = Paths.get(PATH_TO_PDF, constituencyID + ".pdf").toString();
        PdfWriter writer = new PdfWriter(dest);

        // Creating a PdfDocument object
        PdfDocument pdfDoc = new PdfDocument(writer);

        // 631*561 pixels width n height
        float widthInPoints = 631 * 0.75f;
        float heightInPoints = 561 * 0.75f;
        PageSize pgSz = new PageSize(widthInPoints, heightInPoints);
        pdfDoc.setDefaultPageSize(pgSz);

        // Set background
        // pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, new PageBackgroundsEventHandler());

        // Creating a Document object
        Document doc = new Document(pdfDoc);

        // Creating table for text fields
        Table candidateNamePartyNameTable = new Table(new float[]{widthInPoints});
        candidateNamePartyNameTable.setBorder(Border.NO_BORDER);

        Cell candidateNameCell = new Cell();
        candidateNameCell.setBold();
        candidateNameCell.setHorizontalAlignment(HorizontalAlignment.LEFT);
        candidateNameCell.setVerticalAlignment(VerticalAlignment.MIDDLE);
        float candidateNameFontSize =
                candidateName.length() > 30 ?
                        Float.parseFloat(FONT_SIZE) - 2 :
                        candidateName.length() > 20 ?
                                Float.parseFloat(FONT_SIZE) - 1 :
                                Float.parseFloat(FONT_SIZE);
        candidateNameCell.setFontSize(candidateNameFontSize);
        candidateNameCell.setFontColor(TEXT_COLOR);
        candidateNameCell.setBorder(Border.NO_BORDER);
        candidateNameCell.add(candidateName);

        Cell partyNameCell = new Cell();
        partyNameCell.setBold();
        partyNameCell.setHorizontalAlignment(HorizontalAlignment.RIGHT);
        partyNameCell.setVerticalAlignment(VerticalAlignment.MIDDLE);
        float partyNameFontSize = partyName.length() > 30 ?
                Float.parseFloat(FONT_SIZE) - 8.0f :
                partyName.length() > 20 ?
                        Float.parseFloat(FONT_SIZE) - 6.0f :
                        Float.parseFloat(FONT_SIZE) - 3.0f;
        partyNameCell.setFontSize(partyNameFontSize);
        partyNameCell.setFontColor(TEXT_COLOR);
        partyNameCell.setBorder(Border.NO_BORDER);
        partyNameCell.add(partyName);

        //Candidate Name Below Candidate Image
        candidateNamePartyNameTable.addCell(candidateNameCell);

        //candidate Party name below candidate name
        candidateNamePartyNameTable.addCell(partyNameCell);

        // Creating table for images
        float[] pointColumnWidths = {widthInPoints / 2.0f, widthInPoints / 2.0f};
        Table candidatePhotoPartySymbolTable = new Table(pointColumnWidths);
        //Table candidatePhotoPartySymbolTable = new Table(2, false);
        candidatePhotoPartySymbolTable.setBorder(Border.NO_BORDER);

        Cell candidatePhotoCell = new Cell();
        candidatePhotoCell.setBorder(Border.NO_BORDER);
        candidatePhotoCell.setVerticalAlignment(VerticalAlignment.MIDDLE);

        // Candidate photo
        //String candidatePhotoImageFile = Paths.get(PATH_TO_CANDIDATE_PHOTOS, constituencyID+".JPG").toString();
        String candidatePhotoImageFile = guessFilename(constituencyID, PATH_TO_CANDIDATE_PHOTOS);
        ImageData data = ImageDataFactory.create(candidatePhotoImageFile);

        // Creating the image
        Image candidatePhotoImage = new Image(data);
        candidatePhotoImage.scaleToFit(180f, 180f);

        // Adding image to the cell10
        candidatePhotoCell.add(candidatePhotoImage);//candidatePhotoImage.setAutoScale(true));

        // Creating the cell10
        Cell partySymbolCell = new Cell();
        partySymbolCell.setBorder(Border.NO_BORDER);
        partySymbolCell.setVerticalAlignment(VerticalAlignment.MIDDLE);

        // Party photo
        // String partySymbolImageFile = Paths.get(PATH_TO_PARTY_SYMBOLS, constituencyID+".JPG").toString();
        String partySymbolImageFile = guessFilename(constituencyID, PATH_TO_PARTY_SYMBOLS);
        //imageFile = "D:/Chandru/Official/Workspace/arappor/PDF/try1/arappor/Symbols_Final/ARAKKONAM1.JPG";
        data = ImageDataFactory.create(partySymbolImageFile);

        // Creating the image
        Image partySymbolImage = new Image(data);
        partySymbolImage.scaleToFit(120f, 120f);

        // Adding image to the cell10
        partySymbolCell.add(partySymbolImage);//partySymbolImage.setAutoScale(true));


        // Adding cell110 to the table
        candidatePhotoPartySymbolTable.addCell(candidatePhotoCell);

        // Adding cell110 to the table
        candidatePhotoPartySymbolTable.addCell(partySymbolCell);

        // Adding Tables to document
        doc.add(candidateNamePartyNameTable);
        doc.add(candidatePhotoPartySymbolTable);

        if (pdfDoc.getNumberOfPages() > 1) {
            System.err.printf("Warning: Number pages exceeds 1 - %s\n", dest);
        }

        // Closing the document
        doc.close();
    }

    public static class PageBackgroundsEventHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
//			int pagenumber = docEvent.getDocument().getNumberOfPages();
//			if (pagenumber % 2 == 1 && pagenumber != 1) {
//				return;
//			}
            PdfCanvas canvas = new PdfCanvas(page);
            Rectangle rect = page.getPageSize();
            canvas
                    .saveState()
                    .setFillColor(BACKGROUND_COLOR)
                    .rectangle(rect.getLeft(), rect.getBottom(), rect.getWidth(), rect.getHeight())
                    .fillStroke()
                    .restoreState();
        }
    }
}
