package playground.andreas.bln.ana;

import net.opengis.kml._2.AbstractFeatureType;
import net.opengis.kml._2.FolderType;
import net.opengis.kml._2.LineStringType;
import net.opengis.kml._2.ObjectFactory;
import net.opengis.kml._2.PlacemarkType;
import net.opengis.kml._2.PointType;
import net.opengis.kml._2.StyleType;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Time;
import org.matsim.vis.kml.NetworkFeatureFactory;

public class MyFeatureFactory extends NetworkFeatureFactory{
	
	// TODO [AN] visibility DG
	private static final String STARTUL = "<ul>";
	private static final String ENDUL = "</ul>";
	private static final String STARTLI = "<li>";
	private static final String ENDLI = "</li>";

	private static final Logger log = Logger.getLogger(NetworkFeatureFactory.class);
	private ObjectFactory kmlObjectFactory = new ObjectFactory();
	private CoordinateTransformation coordTransform;
	
	public MyFeatureFactory(CoordinateTransformation coordTransform) {
		super(coordTransform);
		this.coordTransform = coordTransform;
	}

	
	@Override
	public AbstractFeatureType createActFeature(ActivityImpl act, StyleType style) {

		PlacemarkType p = this.kmlObjectFactory.createPlacemarkType();
		p.setName(act.getType().toString() + " act");
		p.setDescription(this.createActDescription(act));
		Coord coord = this.coordTransform.transform(act.getCoord());
		PointType point = this.kmlObjectFactory.createPointType();
		point.getCoordinates().add(Double.toString(coord.getX()) + "," + Double.toString(coord.getY()) + ",0.0");
		p.setAbstractGeometryGroup(this.kmlObjectFactory.createPoint(point));

		p.setStyleUrl(style.getId());
		return p;
	}
	
	public AbstractFeatureType createPTLinkFeature(final Coord from, final Coord to, LegImpl leg, StyleType networkStyle) {
		FolderType folder = this.kmlObjectFactory.createFolderType();
		folder.setName(leg.getMode() + " mode, dur: " + Time.writeTime(leg.getTravelTime()) + ", dist: " + leg.getRoute().getDistance());

		PlacemarkType p = this.kmlObjectFactory.createPlacemarkType();
		p.setName(((GenericRouteImpl) leg.getRoute()).getRouteDescription());

		Coord centerCoord = this.coordTransform.transform((new CoordImpl(from.getX() + (to.getX() - from.getX())/2, from.getY() + (to.getY() - from.getY())/2)));
		
		Coord fromCoord = this.coordTransform.transform(from);
		Coord toCoord = this.coordTransform.transform(to);
		LineStringType line = this.kmlObjectFactory.createLineStringType();
		line.getCoordinates().add(Double.toString(fromCoord.getX()) + "," + Double.toString(fromCoord.getY()) + ",0.0");
		line.getCoordinates().add(Double.toString(toCoord.getX()) + "," + Double.toString(toCoord.getY()) + ",0.0");
		p.setAbstractGeometryGroup(this.kmlObjectFactory.createLineString(line));
		p.setStyleUrl(networkStyle.getId());
		p.setDescription(((GenericRouteImpl) leg.getRoute()).getRouteDescription());
		
		PlacemarkType pointPlacemark = this.kmlObjectFactory.createPlacemarkType();
		PointType point = this.kmlObjectFactory.createPointType();
		point.getCoordinates().add(Double.toString(centerCoord.getX()) + "," + Double.toString(centerCoord.getY()) + ",0.0");
		pointPlacemark.setAbstractGeometryGroup(this.kmlObjectFactory.createPoint(point));
		pointPlacemark.setStyleUrl(networkStyle.getId());
		pointPlacemark.setDescription(((GenericRouteImpl) leg.getRoute()).getRouteDescription());

		folder.getAbstractFeatureGroup().add(this.kmlObjectFactory.createPlacemark(pointPlacemark));
		folder.getAbstractFeatureGroup().add(this.kmlObjectFactory.createPlacemark(p));
		
		return folder;
	}
	
	public AbstractFeatureType createCarLinkFeature(final Link l, StyleType networkStyle) {
		FolderType folder = this.kmlObjectFactory.createFolderType();
		String description = this.createLinkDescription(l);
		folder.setName(l.getId().toString());

		PlacemarkType p = this.kmlObjectFactory.createPlacemarkType();
		p.setName(l.getId().toString());

		Coord fromCoord = this.coordTransform.transform(l.getFromNode().getCoord());
		Coord toCoord = this.coordTransform.transform(l.getToNode().getCoord());
		LineStringType line = this.kmlObjectFactory.createLineStringType();
		line.getCoordinates().add(Double.toString(fromCoord.getX()) + "," + Double.toString(fromCoord.getY()) + ",0.0");
		line.getCoordinates().add(Double.toString(toCoord.getX()) + "," + Double.toString(toCoord.getY()) + ",0.0");
		p.setAbstractGeometryGroup(this.kmlObjectFactory.createLineString(line));
		p.setStyleUrl(networkStyle.getId());
		p.setDescription(description);
		
		PlacemarkType pointPlacemark = this.kmlObjectFactory.createPlacemarkType();
		Coord centerCoord = this.coordTransform.transform(l.getCoord());
		PointType point = this.kmlObjectFactory.createPointType();
		point.getCoordinates().add(Double.toString(centerCoord.getX()) + "," + Double.toString(centerCoord.getY()) + ",0.0");
		pointPlacemark.setAbstractGeometryGroup(this.kmlObjectFactory.createPoint(point));
		pointPlacemark.setStyleUrl(networkStyle.getId());
		pointPlacemark.setDescription(description);

		folder.getAbstractFeatureGroup().add(this.kmlObjectFactory.createPlacemark(pointPlacemark));
		folder.getAbstractFeatureGroup().add(this.kmlObjectFactory.createPlacemark(p));
		
		return folder;
	}
	
	public AbstractFeatureType createWalkLinkFeature(final Coord from, final Coord to, LegImpl leg, StyleType networkStyle) {
		PlacemarkType p = this.kmlObjectFactory.createPlacemarkType();
		p.setName(leg.getMode() + " mode, dur: " + Time.writeTime(leg.getTravelTime()) + ", dist: " + leg.getRoute().getDistance());
		
		if(((GenericRouteImpl) leg.getRoute()).getRouteDescription().equalsIgnoreCase("")){
			p.setDescription("sorry no route, made a beeline to the destination");
		} else {
			p.setDescription(((GenericRouteImpl) leg.getRoute()).getRouteDescription());
		}		

		Coord fromCoord = this.coordTransform.transform(from);
		Coord toCoord = this.coordTransform.transform(to);
		LineStringType line = this.kmlObjectFactory.createLineStringType();
		line.getCoordinates().add(Double.toString(fromCoord.getX()) + "," + Double.toString(fromCoord.getY()) + ",0.0");
		line.getCoordinates().add(Double.toString(toCoord.getX()) + "," + Double.toString(toCoord.getY()) + ",0.0");
		p.setAbstractGeometryGroup(this.kmlObjectFactory.createLineString(line));
		p.setStyleUrl(networkStyle.getId());
		
		return p;
	}
	
	// TODO [AN] visibility DG
	private String createLinkDescription(Link l) {
		StringBuilder buffer = new StringBuilder(100);
//		buffer.append(NetworkFeatureFactory.STARTCDATA);
		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append("Link: " );
		buffer.append(l.getId());
		buffer.append(NetworkFeatureFactory.ENDP);
		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append("From Node: ");
		buffer.append(l.getFromNode().getId());
//		buffer.append(NetworkFeatureFactory.ENDH3);
//		buffer.append(NetworkFeatureFactory.STARTP);
//		buffer.append(NetworkFeatureFactory.ENDP);
//		buffer.append(NetworkFeatureFactory.STARTH3);
		buffer.append("<br>");
		buffer.append("To Node: ");
		buffer.append(l.getToNode().getId());
//		buffer.append(NetworkFeatureFactory.ENDH3);
//		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append(NetworkFeatureFactory.ENDP);

		buffer.append(NetworkFeatureFactory.STARTH3);
		buffer.append("Attributes: ");
		buffer.append(NetworkFeatureFactory.ENDH3);
		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append(STARTUL);
		buffer.append(STARTLI);
		buffer.append("Freespeed: ");
		buffer.append(l.getFreespeed(Time.UNDEFINED_TIME));
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("Capacity: ");
		buffer.append(l.getCapacity(org.matsim.core.utils.misc.Time.UNDEFINED_TIME));
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("Number of Lanes: ");
		buffer.append(l.getNumberOfLanes(org.matsim.core.utils.misc.Time.UNDEFINED_TIME));
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("Length: ");
		buffer.append(l.getLength());
		buffer.append(ENDLI);
		buffer.append(ENDUL);
		buffer.append(NetworkFeatureFactory.ENDP);

//		buffer.append(NetworkFeatureFactory.ENDCDATA);

		return buffer.toString();
	}
	
	private String createActDescription(ActivityImpl act) {
		StringBuilder buffer = new StringBuilder(100);
//		buffer.append(NetworkFeatureFactory.STARTCDATA);
//		buffer.append(NetworkFeatureFactory.STARTH2);
		buffer.append("Link: " );
		buffer.append(act.getLinkId());
//		buffer.append(NetworkFeatureFactory.ENDH2);
		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append(NetworkFeatureFactory.ENDP);
		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append(NetworkFeatureFactory.ENDP);

		buffer.append(NetworkFeatureFactory.STARTH3);
		buffer.append("Attributes: ");
		buffer.append(NetworkFeatureFactory.ENDH3);
		buffer.append(NetworkFeatureFactory.STARTP);
		buffer.append(STARTUL);
		buffer.append(STARTLI);
		buffer.append("StartTime: ");
		buffer.append(Time.writeTime(act.getStartTime()));
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("Duration: ");
		buffer.append(Time.writeTime(act.getDuration()));
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("EndTime: ");
		buffer.append(Time.writeTime(act.getEndTime()));
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("X: ");
		buffer.append(act.getCoord().getX());
		buffer.append(ENDLI);
		buffer.append(STARTLI);
		buffer.append("Y: ");
		buffer.append(act.getCoord().getY());
		buffer.append(ENDLI);
		buffer.append(ENDUL);
		buffer.append(NetworkFeatureFactory.ENDP);

//		buffer.append(NetworkFeatureFactory.ENDCDATA);

		return buffer.toString();
	}
}
