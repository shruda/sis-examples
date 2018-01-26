package org.apache.sis;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.measure.Unit;
import javax.measure.quantity.Length;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.measure.Units;
import org.apache.sis.referencing.CRS;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.util.FactoryException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@RunWith(Parameterized.class)
public class TransformVerificationTest
{
	private static final double					PRECISION_METER_PROJECTED	= 1e-2f;
	
	private static final double					PRECISION_METER_WGS84		= 1;
	
	// approximation for the conversion between radian and metre
	private static Unit<Length>					METRE_PER_RADIAN			= Units.METRE.getSystemUnit().multiply(40076592d / (2d * Math.PI));
	
	private static CoordinateReferenceSystem	EPSG_4326;
	
	private static List<String>					LAST_SUCCESSFUL;
	
	private static List<String>					SUCCESSFUL					= new ArrayList<>();
	
	static
	{
		try
		{
			System.setProperty("derby.system.home",
					Files.createTempDirectory("_epsg").toString());
			
			EPSG_4326 = CRS.forCode(String.format("EPSG:%s", 4326));
		}
		catch (IOException | FactoryException e)
		{
			e.printStackTrace();
		}
	}
	
	private String			crsName;
	
	private List<double[]>	wgs84Coordinate;
	
	private List<double[]>	transformedCoordinate;
	
	private boolean			hasWorkedBefore;
	
	public TransformVerificationTest(String crsName, List<double[]> wgs84Coordinate,
			List<double[]> transformedCoordinate, boolean hasWorkedBefore)
	{
		this.crsName = crsName;
		this.wgs84Coordinate = wgs84Coordinate;
		this.transformedCoordinate = transformedCoordinate;
		this.hasWorkedBefore = hasWorkedBefore;
	}
	
	@Parameters(name = "{index}: {0}")
	public static Collection<Object[]> getParameters() throws IOException, FactoryException
	{
		try (InputStreamReader resultReader = new InputStreamReader(Files.newInputStream(Paths.get("results.json"))))
		{
			List<CrsResult> referenceResults = new Gson().fromJson(resultReader, new TypeToken<List<CrsResult>>()
			{
			}.getType());
			
			LAST_SUCCESSFUL = Files.readAllLines(Paths.get("lastSuccessful.json"));
			return referenceResults.stream().filter(c -> !c.transformedCoordinate.isEmpty()).map(c ->
			{
				return new Object[] { c.identifier, c.wgs84Coordinate, c.transformedCoordinate,
						LAST_SUCCESSFUL.contains(c.identifier) };
			}).collect(Collectors.toList());
		}
	}
	
	@AfterClass
	public static void afterClass() throws IOException
	{
		Assert.assertTrue(String
				.format("Some coordinate reference systems doesn't work anymore! [%s]", LAST_SUCCESSFUL),
				LAST_SUCCESSFUL.isEmpty());
		
		Files.write(Paths.get("successful.json"), SUCCESSFUL, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}
	
	@Test
	public void verify() throws Throwable
	{
		try
		{
			CoordinateReferenceSystem crsToTest = CRS.forCode(crsName);
			
			MathTransform fromWgs84 = CRS.findOperation(EPSG_4326, crsToTest, null).getMathTransform();
			MathTransform toWgs84 = CRS.findOperation(crsToTest, EPSG_4326, null).getMathTransform();
			
			Unit<?> unit = crsToTest.getCoordinateSystem().getAxis(0).getUnit();
			double delta = PRECISION_METER_PROJECTED;
			double wgs84delta = Units.METRE.getConverterTo(METRE_PER_RADIAN)
					.concatenate(Units.RADIAN.getConverterTo(Units.DEGREE)).convert(PRECISION_METER_WGS84);
			
			if (unit.isCompatible(Units.RADIAN))
			{
				delta = Units.METRE.getConverterTo(METRE_PER_RADIAN)
						.concatenate(Units.RADIAN.getConverterToAny(unit)).convert(PRECISION_METER_WGS84);
			}
			else
			{
				delta = Units.METRE.getConverterToAny(unit).convert(delta);
			}
			
			for (int i = 0; i < wgs84Coordinate.size(); i++)
			{
				DirectPosition2D toAssert = new DirectPosition2D();
				fromWgs84.transform(new DirectPosition2D(wgs84Coordinate.get(i)[0], wgs84Coordinate.get(i)[1]), toAssert);
				
				Assert.assertArrayEquals(coordinateDifferenceMessage(unit, delta),
						transformedCoordinate.get(i), new double[] { toAssert.getX(), toAssert.getY() }, delta);
				
				toWgs84.transform(new DirectPosition2D(transformedCoordinate.get(i)[0], transformedCoordinate.get(i)[1]), toAssert);
				
				Assert.assertArrayEquals(coordinateDifferenceMessage(Units.RADIAN, delta),
						wgs84Coordinate.get(i), new double[] { toAssert.getX(), toAssert.getY() }, wgs84delta);
			}
			
			LAST_SUCCESSFUL.remove(crsName);
			SUCCESSFUL.add(crsName);
			
		}
		catch (Throwable t)
		{
			if (!hasWorkedBefore)
			{
				// ignore exception if the CRS never has worked!
				return;
			}
			
			throw t;
		}
	}
	
	private static String coordinateDifferenceMessage(Unit<?> unit, double delta)
	{
		String unitName = unit.isCompatible(Units.RADIAN) ? "rad" : "m";
		return String.format("Used delta=%s%s", delta, unitName);
	}
}
