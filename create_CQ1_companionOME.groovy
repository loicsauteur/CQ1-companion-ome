/**
 * Create an OME.companion file for a specific CQ1 dataset for upload to OMERO
 *
 * The OME.companion file will contain the same metadata as the
 * original ome.xml metadata and is thought for using as upload file for OMERO.
 * It will allow the specification of a screen name (so the upload screen name
 * will not always be 'MeasurementResult.ome.tif').
 * And it will prevent the upload of the 'TitleImage', which lands in orphans
 * and remains linked to the screen dataset.
 *
 * @author: Loïc Sauteur - DBM - University Basel - loic.sauteur@unibas.ch
 */



import ij.IJ
import loci.formats.ImageReader
import loci.formats.MetadataTools
import ome.xml.model.Instrument
import ome.xml.model.OME
import ome.specification.XMLWriter
import ome.xml.model.Plate
import ome.xml.model.Screen
import ome.xml.model.Well
import ome.xml.model.WellSample
import ome.xml.model.primitives.NonNegativeInteger

#@File(label = "Select the CQ1 ome.tif", style = "file") in_file
#@String(label = "Name your experiment", value = "SampleX_PlateY") exp_name


main()

def main() {
    // read the original metadata
    ome_ori = read_ome_tif(in_file.getAbsolutePath())

    // allow only single plates per ome.tif
    if (ome_ori.sizeOfPlateList() != 1) {
        IJ.error("Number of well plates not supported",
                "${ome_ori.sizeOfPlateList()} plates detected.\n" +
                "Script works only with exactly one well plate per ome.tif")
        return
    }

    // create new OME metadata file (for the companion file)
    ome_new = new OME()
    // create new plate
    plate_new = createNewPlate(ome_ori.getPlate(0))
    ome_new.addPlate(plate_new)
    // add the instrument
    instrument = createInstrument(ome_ori.getInstrument(0))
    ome_new.addInstrument(instrument)

    // add screen
    screen = new Screen()
    screen.setID(ome_ori.getScreen(0).getID())
    screen.setName(ome_ori.getScreen(0).getName())
    screen.linkPlate(plate_new)
    ome_new.addScreen(screen)

    // create well, wellsample and image entries
    addImagesAndWells(plate_new, instrument, ome_new, ome_ori)

    write_companion(ome_new, in_file, exp_name)
}


/**
 * Checks all the images available and creates wells,
 * wellSamples according to the image entries
 * (creates new ImageIDs, WellIDs and WellSampleIDs).
 * @param plate = new Plate (empty)
 * @param instrument = Created instrument that is in the new OME
 * @param ome_new = new OME store
 * @param ome_ori = original OME store (i.e. read from the ome.tif file)
 */
def addImagesAndWells(Plate plate, Instrument instrument, OME ome_new, OME ome_ori) {
    plateID = plate.getID().split(":")[1] as Integer
    wellID = 0
    wellSampleID = 0
    imageID = 0
    //index = 1
    prev_row = ""
    prev_col = ""

    for (i in 0..<ome_ori.sizeOfImageList()) {
        image = ome_ori.getImage(i)
        if (image.getName() != "TitleImage") { // to skip the TitleImage
            name = image.getName()
            well = name.split(",")[1]
            fov = name.split(",")[2]
            name.split("")
            row = name.split("\\(R")[1].split("C")[0]
            col = name.split("\\(")[1].split("\\),")[0].split("C")[1]
            if (prev_row == "") prev_row = row
            if (prev_col == "") prev_col = col
            println("well=$well, fov=$fov, row=$row, col=$col")

            // reset the ImageID and link instrument
            image.setID("Image:$imageID")
            image.linkInstrument(instrument)

            // for the first entry
            if (imageID == 0) {
                // create new well (for plate)
                well_new = new Well()
                well_new.setColumn(new NonNegativeInteger(col as Integer))
                well_new.setRow(new NonNegativeInteger(row as Integer))
                well_new.setID("Well:$plateID:$wellID")

                // create new WellSample
                sample_new = new WellSample()
                sample_new.setID("WellSample:$plateID:$wellID:$imageID")
                sample_new.linkImage(image)
                well_new.addWellSample(sample_new)

                // increment the wellsample counter and imageId counter
                imageID++
                wellSampleID++
            }
            else {
                // create new well sample
                sample_new = new WellSample()
                sample_new.setID("WellSample:$plateID:$wellID:$imageID")
                sample_new.linkImage(image)
                // increment the wellsample counter and imageID counter
                imageID++
                wellSampleID++
                // check if it is the same well
                if (prev_row == row && prev_col == col) {
                    well_new.addWellSample(sample_new)
                }
                else {
                    // respecify the previous row and col indentifies
                    prev_row = row
                    prev_col = col
                    // add the previous well to the plate, and increment counter
                    plate.addWell(well_new)
                    wellID++
                    // create new well
                    well_new = new Well()
                    well_new.setColumn(new NonNegativeInteger(col as Integer))
                    well_new.setRow(new NonNegativeInteger(row as Integer))
                    well_new.setID("Well:$plateID:$wellID")
                    // add the wellsample
                    well_new.addWellSample(sample_new)

                }
            }
            println("\twell: $wellID, wellsample: $wellSampleID, imageID: $imageID")
            ome_new.addImage(image)
        }
    }
    // add the last well to the plate
    plate.addWell(well_new)

}


/**
 * Reads an OME tif and retireves the xml metadata
 * @param path = String file path
 * @return = OME metadata object
 */
def read_ome_tif(String path) {
    reader = new ImageReader()
    ome_meta = MetadataTools.createOMEXMLMetadata()
    reader.setMetadataStore(ome_meta)
    reader.setId(in_file.getAbsolutePath())

    // reade the ome metadata
    ome_ori = reader.getMetadataStoreRoot() as OME
    return ome_ori
}

/**
 * Unused... (old version)
 * @param oriMetaStore
 * @param newStore
 */
def addImages(OME oriMetaStore, OME newStore) {
    for (i in 0..<oriMetaStore.sizeOfImageList()) {
        oriImg = oriMetaStore.getImage(i)
        if (oriImg.getName() == "TitleImage") {
            println("Warning: TitleImage still present. Aborting forced by other error")
            abortHackDueTo_TitleImage_notPresent
        }
        oriImg.linkInstrument(newStore.getInstrument(0))
        newStore.addImage(oriImg)
    }
}


/**
 * Creates a new Instrument, based on an existing one. i.e. fresh copy.
 * @param oriInstrument = Instrument, i.e. the one from the original metadata
 * @return Instrument
 */
def createInstrument(Instrument oriInstrument) {
    instrument = new Instrument()
    instrument.setID(oriInstrument.getID())
    // copy the microscope
    instrument.setMicroscope(oriInstrument.getMicroscope())

    for (i in 0..<oriInstrument.sizeOfLightSourceList()) {
        oriLight = oriInstrument.getLightSource(i)
        instrument.addLightSource(oriLight)
    }

    for (i in 0..<oriInstrument.sizeOfDetectorList()) {
        oriDec = oriInstrument.getDetector(i)
        instrument.addDetector(oriDec)
    }

    for (i in 0..<oriInstrument.sizeOfDichroicList()) {
        oriDic = oriInstrument.getDichroic(i)
        instrument.addDichroic(oriDic)
    }

    for (i in 0..<oriInstrument.sizeOfFilterList()) {
        oriFil = oriInstrument.getFilter(i)
        instrument.addFilter(oriFil)
    }
    for (i in 0..<oriInstrument.sizeOfFilterSetList()) {
        oriSet = oriInstrument.getFilterSet(i)
        instrument.addFilterSet(oriSet)
    }

    for (i in 0..<oriInstrument.sizeOfObjectiveList()) {
        oriObj = oriInstrument.getObjective(i)
        instrument.addObjective(oriObj)
    }

    return instrument
}

/**
 * Creates a new plate, based on the ome.tif data
 * @param oriPlate = Plate, read from the ome.tif
 * @return Plate
 */
def createNewPlate(Plate oriPlate) {
    plate = new Plate()
    // copy the plate attributes
    plate.setID(oriPlate.getID())
    plate.setName(oriPlate.getName())
    plate.setWellOriginX(oriPlate.getWellOriginX())
    plate.setWellOriginY(oriPlate.getWellOriginY())
    plate.setRows(oriPlate.getRows())
    plate.setColumns(oriPlate.getColumns())

    return plate
    // FIXME: old version that did not work below
    /*


    // copy well attributes
    nWells = oriPlate.sizeOfWellList()
    for (i in 0..<nWells) {
        oriWell = oriPlate.getWell(i)
        // only if it contains well samples
        if (oriWell.sizeOfWellSampleList() > 0) {
            cur_well = new Well()
            cur_well.setColumn(oriWell.getColumn())
            cur_well.setRow(oriWell.getRow())
            cur_well.setID(oriWell.getID())

            // add WellSamples
            for (j in 0..<oriWell.sizeOfWellSampleList()) {
                oriWellSample = oriWell.getWellSample(j)
                cur_wellSample = new WellSample()
                cur_wellSample.setID(oriWellSample.getID())
                cur_wellSample.setPositionX(oriWellSample.getPositionX())
                cur_wellSample.setPositionY(oriWellSample.getPositionY())
                cur_wellSample.setIndex(oriWellSample.getIndex())

                // add imageREf
                cur_wellSample.linkImage(oriWellSample.getLinkedImage())

                cur_well.addWellSample(cur_wellSample)
            }

            plate.addWell(cur_well)
        }

    }
    return plate
    */
}

/**
 * Writes the companion.ome to file
 * @param ome_new = OME store
 * @param in_file = File input file (for path)
 * @param exp_name = String, name of the file
 */
def write_companion(OME ome_new, File in_file, String exp_name) {
    // write the companion.ome file
    writer = new XMLWriter()
    out_file = new File(in_file.parent + File.separator + exp_name + '.companion.ome')
    writer.writeFile(out_file, ome_new, false)
    println("Please use following file for importing your dataset to OMERO:")
    println(out_file.getAbsolutePath())
    //IJ.log("Please use following file for importing your dataset to OMERO:")
    //IJ.log(out_file.getAbsolutePath())
}

