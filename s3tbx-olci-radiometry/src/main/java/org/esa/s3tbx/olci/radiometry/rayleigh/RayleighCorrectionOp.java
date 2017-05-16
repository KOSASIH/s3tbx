/*
 *
 *  * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *
 */

package org.esa.s3tbx.olci.radiometry.rayleigh;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s3tbx.olci.radiometry.Sensor;
import org.esa.s3tbx.olci.radiometry.gasabsorption.GaseousAbsorptionAux;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dataio.envisat.EnvisatConstants;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.esa.s3tbx.olci.radiometry.SensorConstants.*;
import static org.esa.s3tbx.olci.radiometry.smilecorr.SmileCorrectionUtils.*;

/**
 * @author muhammad.bc.
 */
@OperatorMetadata(alias = "RayleighCorrection",
        description = "Performs radiometric corrections on OLCI and MERIS L1b data products.",
        authors = "Marco Peters, Muhammad Bala (Brockmann Consult)",
        copyright = "(c) 2016 by Brockmann Consult",
        category = "Optical/Pre-Processing",
        version = "1.2")
public class RayleighCorrectionOp extends Operator {

    private static final String ALTITUDE = "altitude";
    private static final String R_BRR_PATTERN = "rBRR_\\d{2}";
    private static final String AUTO_GROUPING = "rtoa:taur:rtoa_ng:rtoaRay:rBRR";
    private static final int WV_709_FOR_GASEOUS_ABSORPTION_CALCULATION = 709;
    private static final String SOLAR_FLUX_BAND_PATTERN = "solar_flux_band_%d";
    private static final String LAMBDA0_BAND_PATTERN = "lambda0_band_%d";

    private static final String SZA = "SZA";
    private static final String SAA = "SAA";
    private static final String OZA = "OZA";
    private static final String OAA = "OAA";

    private static final String SEA_LEVEL_PRESSURE = "sea_level_pressure";
    private static final String TOTAL_OZONE = "total_ozone";
    private static final String TP_LATITUDE = "TP_latitude";
    private static final String TP_LONGITUDE = "TP_longitude";
    private static final String TP_ALTITUDE = "TP_altitude";
    private static final String[] BAND_CATEGORIES = new String[]{
            "taur_%02d",
            "rBRR_%02d",
            "rtoa_ng_%02d",
            "rtoa_%02d",
    };
    private static final String AIRMASS = "airmass";
    private static final String ALTITUDE_DEM = "dem_alt";
    private static final String RTOA_PATTERN = "rtoa_\\d{2}";
    private static final String TAUR_PATTERN = "taur_\\d{2}";
    private static final String RTOA_NG_PATTERN = "rtoa_ng_\\d{2}";
    @SourceProduct
    Product sourceProduct;


    @Parameter(description = "Comma-separated list of radiance source bands to be Rayleigh corrected." +
            "If not specified, all bands will be processed and written to target product.",
            label = "Subset of TOA radiances to be processed",
            defaultValue = "")
    String csvListOfBandsToProcess;

    @Parameter(defaultValue = "true", label = "Compute Rayleigh optical thickness bands")
    private boolean computeTaur;


    @Parameter(defaultValue = "true", label = "Compute bottom of Rayleigh reflectance bands")
    private boolean computeRBrr;

    @Parameter(defaultValue = "false", label = "Compute gaseous absorption corrected TOA reflectance bands")
    private boolean computeRtoaNg;

    @Parameter(defaultValue = "false", label = "Compute TOA reflectance bands")
    private boolean computeRtoa;

    @Parameter(defaultValue = "false", label = "Add air mass")
    private boolean addAirMass;


    private RayleighCorrAlgorithm algorithm;
    private Sensor sensor;
    private double[] absorpOzone;
    private double[] crossSectionSigma;

    private String[] specificBandsToProcess = null;


    @Override
    public void initialize() throws OperatorException {
        sensor = getSensorType(sourceProduct);

        if (csvListOfBandsToProcess != null && csvListOfBandsToProcess.length() > 0) {
            specificBandsToProcess = csvListOfBandsToProcess.trim().split("\\s*,\\s*");
        }

        algorithm = new RayleighCorrAlgorithm(sensor.getNameFormat(), sensor.getNumBands());

        absorpOzone = GaseousAbsorptionAux.getInstance().absorptionOzone(sensor.getName());
        crossSectionSigma = getCrossSectionSigma(sourceProduct, sensor.getNumBands(), sensor.getNameFormat());

        Product targetProduct = new Product(sourceProduct.getName() + "_rayleigh", sourceProduct.getProductType(),
                                            sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());

        RayleighAux.initDefaultAuxiliary();
        addTargetBands(targetProduct);
        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        targetProduct.setAutoGrouping(AUTO_GROUPING);
        setTargetProduct(targetProduct);
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        checkForCancellation();
        RayleighAux rayleighAux = createAuxiliary(sensor, targetRectangle);

        Set<Map.Entry<Band, Tile>> entries = targetTiles.entrySet();
        entries.forEach(targetTileStream -> {
            // todo: add appropriate filtering of invalid pixels
            Tile targetTile = targetTileStream.getValue();
            Band targetBand = targetTileStream.getKey();

            String targetBandName = targetBand.getName();
            double[] rayleighOpticalThickness = null;
            int sourceBandIndex = getSourceBandIndex(targetBand.getName());
            if (targetBandName.equals(AIRMASS) && addAirMass) {
                double[] massAirs = rayleighAux.getAirMass();
                targetTile.setSamples(massAirs);
                return;
            }
            if (sourceBandIndex == -1) {
                return;
            }
            addAuxiliaryData(rayleighAux, targetRectangle, sourceBandIndex);
            if (targetBandName.matches(RTOA_PATTERN) && computeRtoa) {
                targetTile.setSamples(getReflectance(rayleighAux));
            } else if (targetBandName.matches(TAUR_PATTERN) && computeTaur) {
                rayleighOpticalThickness = algorithm.getRayleighThickness(rayleighAux, crossSectionSigma, sourceBandIndex);
                targetTile.setSamples(rayleighOpticalThickness);
            } else if (computeRBrr || computeRtoaNg) {

                double[] reflectance = getReflectance(rayleighAux);
                if (Math.ceil(rayleighAux.getWaveLength()) == WV_709_FOR_GASEOUS_ABSORPTION_CALCULATION) {
                    reflectance = waterVaporCorrection709(reflectance, targetRectangle, sensor);
                }
                double[] corrOzoneRefl = getCorrectOzone(rayleighAux, reflectance, sourceBandIndex);
                if (targetBandName.matches(RTOA_NG_PATTERN) && computeRtoaNg) {
                    targetTile.setSamples(corrOzoneRefl);
                }
                if (targetBandName.matches(R_BRR_PATTERN) && computeRBrr) {
                    if (Objects.isNull(rayleighOpticalThickness)) {
                        rayleighOpticalThickness = algorithm.getRayleighThickness(rayleighAux, crossSectionSigma, sourceBandIndex);
                    }
                    double[] rhoBrr = getRhoBrr(rayleighAux, rayleighOpticalThickness, corrOzoneRefl);
                    targetTile.setSamples(rhoBrr);
                }
            }
        });
    }

    private double[] waterVaporCorrection709(double[] reflectances, Rectangle targetRectangle, Sensor sensor) {
        String bandNameFormat = sensor.getNameFormat();
        int[] upperLowerBounds = sensor.getBounds();
        double[] bWVRefTile = getSampleDoubles(getSourceTile(sourceProduct.getBand(String.format(bandNameFormat, upperLowerBounds[1])), targetRectangle));
        double[] bWVTile = getSampleDoubles(getSourceTile(sourceProduct.getBand(String.format(bandNameFormat, upperLowerBounds[0])), targetRectangle));
        return algorithm.waterVaporCorrection709(reflectances, bWVRefTile, bWVTile);
    }

    private double[] getRhoBrr(RayleighAux rayleighAux, double[] rayleighOpticalThickness, double[] corrOzoneRefl) {
        return algorithm.getRhoBrr(rayleighAux, rayleighOpticalThickness, corrOzoneRefl);
    }


    private double[] getCorrectOzone(RayleighAux rayleighAux, double[] reflectance, int sourceBandIndex) {
        double absorpO = absorpOzone[sourceBandIndex - 1];
        double[] totalOzones = rayleighAux.getTotalOzones();
        double[] cosOZARads = rayleighAux.getCosOZARads();
        double[] cosSZARads = rayleighAux.getCosSZARads();

        return algorithm.getCorrOzone(reflectance, absorpO, totalOzones, cosOZARads, cosSZARads);
    }

    private double[] getReflectance(RayleighAux rayleighAux) {
        double[] sourceSampleRad = rayleighAux.getSourceSampleRad();
        double[] solarFluxs = rayleighAux.getSolarFluxs();
        double[] sunZenithAngles = rayleighAux.getSunZenithAngles();

        return algorithm.convertRadsToRefls(sourceSampleRad, solarFluxs, sunZenithAngles);
    }

    private void addTargetBands(Product targetProduct) {
        if (computeTaur) {
            addTargetBands(targetProduct, BAND_CATEGORIES[0]);
        }
        if (computeRBrr) {
            addTargetBands(targetProduct, BAND_CATEGORIES[1]);
        }
        if (computeRtoaNg) {
            addTargetBands(targetProduct, BAND_CATEGORIES[2]);
        }
        if (computeRtoa) {
            addTargetBands(targetProduct, BAND_CATEGORIES[3]);
        }
        if (addAirMass) {
            targetProduct.addBand(AIRMASS, ProductData.TYPE_FLOAT32);
        }
    }

    private void addTargetBands(Product targetProduct, String bandCategory) {
        for (int i = 1; i <= sensor.getNumBands(); i++) {
            final String sourceBandName = String.format(sensor.getNameFormat(), i);
            if (isBandToProcess(sourceBandName)) {
                Band sourceBand = sourceProduct.getBand(sourceBandName);
                Band targetBand = targetProduct.addBand(String.format(bandCategory, i), ProductData.TYPE_FLOAT32);
                ProductUtils.copySpectralBandProperties(sourceBand, targetBand);
            }
        }
    }

    private boolean isBandToProcess(String sourceBandName) {
        if (specificBandsToProcess == null) {
            return true;
        }
        if (specificBandsToProcess.length > 0) {
            for (String b : specificBandsToProcess) {
                if (b.equals(sourceBandName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int addAuxiliaryData(RayleighAux rayleighAux, Rectangle rectangle, int sourceBandRefIndex) {
        String format = String.format(sensor.getNameFormat(), sourceBandRefIndex);
        Band band = getSourceProduct().getBand(format);
        String sourceBandName = band.getName();
        rayleighAux.setWavelength(band.getSpectralWavelength());
        rayleighAux.setSourceBandIndex(sourceBandRefIndex);
        rayleighAux.setSourceBandName(sourceBandName);

        if (sensor.equals(Sensor.OLCI)) {
            rayleighAux.setSolarFluxs(getSourceTile(sourceProduct.getBand(String.format(SOLAR_FLUX_BAND_PATTERN, sourceBandRefIndex)), rectangle));
            rayleighAux.setLambdaSource(getSourceTile(sourceProduct.getBand(String.format(LAMBDA0_BAND_PATTERN, sourceBandRefIndex)), rectangle));
            rayleighAux.setSourceSampleRad(getSourceTile(sourceProduct.getBand(sourceBandName), rectangle));
        } else if (sensor.equals(Sensor.MERIS)) {
            Band sourceBand = sourceProduct.getBand(sourceBandName);
            rayleighAux.setSourceSampleRad(getSourceTile(sourceBand, rectangle));
            int length = rectangle.width * rectangle.height;

            double[] solarFlux = fillDefaultArray(length, sourceBand.getSolarFlux());
            double[] lambdaSource = fillDefaultArray(length, sourceBand.getSpectralWavelength());

            rayleighAux.setSolarFluxs(solarFlux);
            rayleighAux.setLambdaSource(lambdaSource);
        } else if (sensor.equals(Sensor.MERIS_4TH)) {
            Band sourceBand = sourceProduct.getBand(sourceBandName);
            rayleighAux.setSourceSampleRad(getSourceTile(sourceBand, rectangle));
            int length = rectangle.width * rectangle.height;

            double[] solarFlux = fillDefaultArray(length,
                                                  EnvisatConstants.MERIS_SOLAR_FLUXES[sourceBand.getSpectralBandIndex()]);
            double[] lambdaSource = fillDefaultArray(length, sourceBand.getSpectralWavelength());

            rayleighAux.setSolarFluxs(solarFlux);
            rayleighAux.setLambdaSource(lambdaSource);
        }
        return sourceBandRefIndex;
    }

    private double[] fillDefaultArray(int length, double value) {
        double[] createArray = new double[length];
        Arrays.fill(createArray, value);
        return createArray;
    }

    private RayleighAux createAuxiliary(Sensor sensor, Rectangle rectangle) {
        RayleighAux rayleighAux = new RayleighAux();
        if (sensor.equals(Sensor.MERIS)) {
            rayleighAux.setSunAzimuthAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_SAA_NAME), rectangle));
            rayleighAux.setSunZenithAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_SZA_NAME), rectangle));
            rayleighAux.setViewZenithAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_VZA_NAME), rectangle));
            rayleighAux.setViewAzimuthAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_VAA_NAME), rectangle));
            rayleighAux.setSeaLevels(getSourceTile(sourceProduct.getTiePointGrid(MERIS_SLP_NAME), rectangle));
            rayleighAux.setTotalOzones(getSourceTile(sourceProduct.getTiePointGrid(MERIS_OZONE_NAME), rectangle));
            rayleighAux.setLatitudes(getSourceTile(sourceProduct.getTiePointGrid(MERIS_LAT_NAME), rectangle));
            rayleighAux.setLongitude(getSourceTile(sourceProduct.getTiePointGrid(MERIS_LON_NAME), rectangle));
            rayleighAux.setAltitudes(getSourceTile(sourceProduct.getTiePointGrid(ALTITUDE_DEM), rectangle));
        } else if (sensor.equals(Sensor.MERIS_4TH)) {
            rayleighAux.setSunAzimuthAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_4TH_SAA_NAME), rectangle));
            rayleighAux.setSunZenithAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_4TH_SZA_NAME), rectangle));
            rayleighAux.setViewZenithAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_4TH_VZA_NAME), rectangle));
            rayleighAux.setViewAzimuthAngles(getSourceTile(sourceProduct.getTiePointGrid(MERIS_4TH_VAA_NAME), rectangle));
            rayleighAux.setSeaLevels(getSourceTile(sourceProduct.getTiePointGrid(MERIS_4TH_SLP_NAME), rectangle));
            rayleighAux.setTotalOzones(getSourceTile(sourceProduct.getTiePointGrid(MERIS_4TH_OZONE_NAME), rectangle));
            rayleighAux.setLatitudes(getSourceTile(sourceProduct.getTiePointGrid(TP_LATITUDE), rectangle));
            rayleighAux.setLongitude(getSourceTile(sourceProduct.getTiePointGrid(TP_LONGITUDE), rectangle));
            rayleighAux.setAltitudes(getSourceTile(sourceProduct.getTiePointGrid(TP_ALTITUDE), rectangle));
        } else if (sensor.equals(Sensor.OLCI)) {
            rayleighAux.setSunZenithAngles(getSourceTile(sourceProduct.getTiePointGrid(OLCI_SZA_NAME), rectangle));
            rayleighAux.setViewZenithAngles(getSourceTile(sourceProduct.getTiePointGrid(OLCI_VZA_NAME), rectangle));
            rayleighAux.setSunAzimuthAngles(getSourceTile(sourceProduct.getTiePointGrid(OLCI_SAA_NAME), rectangle));
            rayleighAux.setViewAzimuthAngles(getSourceTile(sourceProduct.getTiePointGrid(OLCI_VAA_NAME), rectangle));
            rayleighAux.setSeaLevels(getSourceTile(sourceProduct.getTiePointGrid(OLCI_SLP_NAME), rectangle));
            rayleighAux.setTotalOzones(getSourceTile(sourceProduct.getTiePointGrid(OLCI_OZONE_NAME), rectangle));
            rayleighAux.setLatitudes(getSourceTile(sourceProduct.getTiePointGrid(TP_LATITUDE), rectangle));
            rayleighAux.setLongitude(getSourceTile(sourceProduct.getTiePointGrid(TP_LONGITUDE), rectangle));
            rayleighAux.setAltitudes(getSourceTile(sourceProduct.getBand(OLCI_ALT_NAME), rectangle));
        }

        return rayleighAux;
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(RayleighCorrectionOp.class);
        }
    }

    private double[] getCrossSectionSigma(Product sourceProduct, int numBands, String getBandNamePattern) {
        return algorithm.getCrossSectionSigma(sourceProduct, numBands, getBandNamePattern);
    }
}
