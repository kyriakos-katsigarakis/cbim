package eu.openmetrics.runtime.actor.system;

import java.io.File;
import java.io.FileOutputStream;
import org.apache.jena.query.DatasetAccessor;
import org.apache.jena.query.DatasetAccessorFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import eu.openmetrics.model.Point;
import eu.openmetrics.runtime.actor.OMActorBase;
import eu.openmetrics.runtime.dao.SiteRepository;
import eu.openmetrics.runtime.domain.Connection;
import eu.openmetrics.runtime.domain.Device;
import eu.openmetrics.runtime.domain.MappingAddress;
import eu.openmetrics.runtime.domain.MappingAddressPoint;
import eu.openmetrics.runtime.domain.Project;
import eu.openmetrics.runtime.domain.ProjectPoint;
import eu.openmetrics.runtime.domain.topology.Building;
import eu.openmetrics.runtime.domain.topology.Site;
import eu.openmetrics.runtime.domain.topology.Space;
import eu.openmetrics.runtime.domain.topology.Storey;
import eu.openmetrics.runtime.model.Entity;
import eu.openmetrics.runtime.service.PublisherService;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OMActorKGE extends OMActorBase {

	private static final Logger log = LoggerFactory.getLogger(OMActorKGE.class);
		
	private String graphUrl;
	private String graphEnrichedFile;
	
	public OMActorKGE(Entity entity) {
		super(entity);
		log.info("Knowledge Graph Enrichment, init=" + getSelf().path().toString());
		graphUrl = entity.getProperty("GraphUrl").getValue();
		graphEnrichedFile = entity.getProperty("GraphEnrichedFile").getValue();
	}
 
	@Autowired 
	private SiteRepository repository;
	
	@Autowired
	private PublisherService publisher;
	
	@Override
	public void onReceive(Object arg0) throws Throwable {
		log.info("kge, execution started...");
		// graph-load
		DatasetAccessor accessor = DatasetAccessorFactory.createHTTP(graphUrl);
		Model model = accessor.getModel();
		// get all devices
		for(Site site : repository.findAllByOrderBySiteNameAsc()) {
			for(Building building : site.getBuildings()) {
				for(Storey storey : building.getStoreys()) {
					for(Space space : storey.getSpaces()) {
						for(Device device : space.getDevices()) {
							if("openlink-modbus-ac-controller".contentEquals(device.getDeviceType())) {
								ResIterator resIterator = model.listResourcesWithProperty(ResourceFactory.createProperty(model.getNsPrefixURI("props") + "hasSerialNumber"));							
								while(resIterator.hasNext()) {
									RDFNode rdfNode = resIterator.next();											
									Resource resThermostat = rdfNode.asResource();
									String deviceSerialNumber = resThermostat.getProperty(ResourceFactory.createProperty(model.getNsPrefixURI("props") + "hasSerialNumber")).getString();
									if(device.getDeviceSerialNumber().equalsIgnoreCase(deviceSerialNumber)) {										
										if(resThermostat.hasProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"))) {
											Statement statement = resThermostat.getProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"));		
											Resource resSpace = statement.getResource();
											Resource resEnergyMeter = model.createResource( model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_ElectricalMeter");
											resEnergyMeter.addProperty(RDF.type, ResourceFactory.createResource( model.getNsPrefixURI("brick") + "Building_Electrical_Meter"));
											resEnergyMeter.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(device.getDeviceSerialNumber()));
											resEnergyMeter.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
											if(device.hasProperty("Address")){
												String deviceAddress = device.getProperty("Address").getPropertyValue();
												Connection connection = device.getConnection();
												Project project = device.getConnection().getProject();
												int posAddress = project.getPositionByType("Address");
												int posType = project.getPositionByType("Type");
												for(MappingAddress mappingAddress : project.getMappingAddressList()){
													if(deviceAddress.contentEquals(mappingAddress.getMappingAddress())){				
														for(MappingAddressPoint mappingAddressPoint : mappingAddress.getPoints()){
															ProjectPoint projectPoint = mappingAddressPoint.getProjectPoint();
															String globalId = connection.getConnectionName() + "." + projectPoint.getPoint(posType) + projectPoint.getPoint(posAddress);
															Point point = publisher.getPoint(globalId);
															if(point != null){
																if(point.hasTag("parameter")) {
																	if(point.hasTag("delay")) {
																		Resource resDelaySensor = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Delay_Parameter");
																		resDelaySensor.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Delay_Parameter"));
																		resDelaySensor.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resDelaySensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resDelaySensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resDelaySensor);														
																	}
																}
																if(point.hasTag("sensor")){
																	if(point.hasTag("occ")){		
																		Resource resOccupancySensor = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Occupancy");
																		resOccupancySensor.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Occupancy_Sensor"));																		
																		resOccupancySensor.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resOccupancySensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resOccupancySensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resOccupancySensor);
																	}else if(point.hasTag("temp")){	
																		Resource resTemperatureSensor = model.createResource( model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Temperature");
																		resTemperatureSensor.addProperty(RDF.type, ResourceFactory.createResource( model.getNsPrefixURI("brick") + "Zone_Air_Temperature_Sensor"));
																		resTemperatureSensor.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resTemperatureSensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resTemperatureSensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resTemperatureSensor);
																	}else if(point.hasTag("humidity")){
																		Resource resHumiditySensor = model.createResource( model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Humidity");
																		resHumiditySensor.addProperty(RDF.type, ResourceFactory.createResource( model.getNsPrefixURI("brick") + "Zone_Air_Humidity_Sensor"));
																		resHumiditySensor.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resHumiditySensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resHumiditySensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resHumiditySensor);
																	}else if(point.hasTag("power")){
																	Resource resPowerSensor = model.createResource( model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Power");
																		resPowerSensor.addProperty(RDF.type, ResourceFactory.createResource( model.getNsPrefixURI("brick") + "Active_Power_Sensor"));
																		resPowerSensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resPowerSensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resPowerSensor.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resEnergyMeter.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resPowerSensor);
																	}else{
																		log.warn("kge, unhandled sensor point=" + point.getGlobal());
																	}
																}else if(point.hasTag("cmd") || point.hasTag("sp") || point.hasTag("writeble")){
																	if(point.hasTag("sp") && point.hasTag("max") == false && point.hasTag("min") == false){
																		Resource resSetpoint = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Setpoint");
																		resSetpoint.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Zone_Air_Temperature_Setpoint"));
																		resSetpoint.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resSetpoint.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resSetpoint.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resSetpoint);	
																	}else if(point.hasTag("sp") && point.hasTag("max") == true && point.hasTag("min") == false){
																		Resource resSetpointMax = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_SetpointMax");
																		resSetpointMax.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Max_Temperature_Setpoint_Limit"));
																		resSetpointMax.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resSetpointMax.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasLocation"), resSpace);
																		resSetpointMax.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resSetpointMax);
																	}else if(point.hasTag("sp") && point.hasTag("max") == false && point.hasTag("min") == true){
																		Resource resSetpointMin = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_SetpointMin");
																		resSetpointMin.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Min_Temperature_Setpoint_Limit"));
																		resSetpointMin.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries") , model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resSetpointMin.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resSetpointMin);	
																	}else if(point.hasTag("onoff")){
																		Resource resOnOff = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_OnOff");
																		resOnOff.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "On_Off_Command"));
																		resOnOff.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resOnOff.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resOnOff);	
																	}else if(point.hasTag("mode")){
																		Resource resMode = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Mode");
																		resMode.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Mode_Command"));
																		resMode.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries") , model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resMode.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resMode);	
																	}else if(point.hasTag("send")){
																		Resource resSend = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Lockout");
																		resSend.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Lockout_Command"));
																		resSend.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resSend.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resSend);	
																	}else if(point.hasTag("sendsettings")){
																		Resource resSendSettings = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Run");
																		resSendSettings.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Run_Request_Command"));
																		resSendSettings.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resSendSettings.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resSendSettings);	
																	}else if(point.hasTag("fan")){
																		Resource resSpeedSetpoint = model.createResource(model.getNsPrefixURI("om") + device.getDeviceSerialNumber() + "_Speed_Setpoint");
																		resSpeedSetpoint.addProperty(RDF.type, ResourceFactory.createResource(model.getNsPrefixURI("brick") + "Speed_Setpoint"));
																		resSpeedSetpoint.addProperty( ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "timeseries"), model.createResource().addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasTimeseriesId"), point.getGlobal()));
																		resSpeedSetpoint.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "isPointOf"), resThermostat);
																		//upd parent
																		resThermostat.addProperty(ResourceFactory.createProperty(model.getNsPrefixURI("brick") + "hasPoint"), resSpeedSetpoint);	
																	}else{
																		log.warn("kge, unhandled writeble point=" + point.getGlobal());
																	}
																}
															}else{
																log.trace("kge, point with global=" + globalId + " not exist!");
															}
														}
													}
												}
											}											
										}
									}
								}
							}
						}
					}
				}
			}
		}
		log.info("kge, updating knowledge graph...");
		File fileDir = new File("C:/Users/Kyriakos/Documents/");
		if(!fileDir.exists()) fileDir.mkdirs();
		File file = new File("C:/Users/Kyriakos/Documents/" + graphEnrichedFile);
		file.createNewFile();
		FileOutputStream oFile = new FileOutputStream(file, false); 
		model.write(oFile,"TTL");
		oFile.close();
		model.close();
		log.info("kge, done!");
	}
}