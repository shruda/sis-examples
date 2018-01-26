package org.apache.sis;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.referencing.CRS;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import com.google.gson.Gson;

public class CrsResult
{
	public String				identifier;
	
	public List<double[]>	wgs84Coordinate			= new ArrayList<>();
	
	public List<double[]>	transformedCoordinate	= new ArrayList<>();
	
	public List<Double>			domainOfValidity;
	
	public static CrsResult of(String code)
			throws FactoryException, MismatchedDimensionException, TransformException
	{
		CoordinateReferenceSystem crsToTest = CRS.forCode(code);
		CoordinateOperation operation = CRS.findOperation(CRS.forCode("EPSG:4326"), crsToTest, null);
		
		// Can't use CRS.getGeographicBoundingBox(Operation) because the center of that BoundigBox can
		// be outside of the CRS BoundingBox
		GeographicBoundingBox boundingBox = CRS.getGeographicBoundingBox(crsToTest);
		
		CrsResult crsTestResult = new CrsResult();
		crsTestResult.identifier = code;
		
		double fithsVertical = (boundingBox.getNorthBoundLatitude() - boundingBox.getSouthBoundLatitude()) * 0.2;
		double fithsHorizontal = (boundingBox.getWestBoundLongitude() - boundingBox.getEastBoundLongitude()) * 0.2;
		
		for (int i = 1; i < 5; i++)
		{
			for (int k = 1; k < 5; k++)
			{
				DirectPosition2D coord = new DirectPosition2D(boundingBox.getSouthBoundLatitude() + i * fithsVertical,
						boundingBox.getEastBoundLongitude() + k * fithsHorizontal);
				
				crsTestResult.wgs84Coordinate.add(doubleListOf(coord));
				crsTestResult.transformedCoordinate
						.add(doubleListOf(operation.getMathTransform().transform(coord, null)));
			}
		}
		crsTestResult.domainOfValidity = Arrays.asList(boundingBox.getWestBoundLongitude(), boundingBox.getSouthBoundLatitude(),
				boundingBox.getEastBoundLongitude(), boundingBox.getNorthBoundLatitude());
		return crsTestResult;
	}
	
	private static double[] doubleListOf(DirectPosition directPosition)
	{
		return directPosition.getCoordinate();
	}
	
	// create Test Results
	public static void main(String[] args) throws NoSuchAuthorityCodeException, FactoryException, IOException
	{
		System.setProperty("derby.system.home",
				Files.createTempDirectory("_epsg").toString());
		CRSAuthorityFactory factory = CRS.getAuthorityFactory("EPSG");
		
		List<CrsResult> crsResults = new ArrayList<>();
		System.out.println(factory.getAuthorityCodes(CoordinateReferenceSystem.class).size());
		
		for (String code : factory.getAuthorityCodes(CoordinateReferenceSystem.class))
		{
			try
			{
				crsResults.add(CrsResult.of(String.format("EPSG:%s", code)));
			}
			catch (Throwable t)
			{
				t.printStackTrace();
			}
		}
		
		try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(Paths.get("results.json"))))
		{
			new Gson().toJson(crsResults, outputStreamWriter);
		}
	}
}
