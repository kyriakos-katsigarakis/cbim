package eu.openmetrics.kgg.service;

import java.util.Iterator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gr.tuc.ifc.IfcModel;
import gr.tuc.ifc4.IfcBeam;
import gr.tuc.ifc4.IfcBuilding;
import gr.tuc.ifc4.IfcBuildingStorey;
import gr.tuc.ifc4.IfcColumn;
import gr.tuc.ifc4.IfcDoor;
import gr.tuc.ifc4.IfcObjectDefinition;
import gr.tuc.ifc4.IfcOpeningElement;
import gr.tuc.ifc4.IfcProduct;
import gr.tuc.ifc4.IfcProject;
import gr.tuc.ifc4.IfcRelAggregates;
import gr.tuc.ifc4.IfcRelAssigns;
import gr.tuc.ifc4.IfcRelAssignsToGroup;
import gr.tuc.ifc4.IfcRelContainedInSpatialStructure;
import gr.tuc.ifc4.IfcRelFillsElement;
import gr.tuc.ifc4.IfcRelVoidsElement;
import gr.tuc.ifc4.IfcRoof;
import gr.tuc.ifc4.IfcSite;
import gr.tuc.ifc4.IfcSlab;
import gr.tuc.ifc4.IfcSpace;
import gr.tuc.ifc4.IfcWall;
import gr.tuc.ifc4.IfcWallStandardCase;
import gr.tuc.ifc4.IfcWindow;
import gr.tuc.ifc4.IfcZone;

@Service
public class Converter {

	private static Logger log = LoggerFactory.getLogger(Converter.class);
	
	private Model rdfModel;
	
	public Converter() {
		log.info("init ifc to knowledge graph converter");
		rdfModel = ModelFactory.createDefaultModel();
	}

	public void convert(IfcModel ifcModel) {
		rdfModel.setNsPrefix("om", "http://openmetrics.eu/openmetrics#");
		rdfModel.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
		rdfModel.setNsPrefix("rdf", RDF.uri);
		rdfModel.setNsPrefix("rdfs", RDFS.uri);
		rdfModel.setNsPrefix("schema", "http://schema.org#");
		rdfModel.setNsPrefix("brick", "https://brickschema.org/schema/1.1/Brick#");
		rdfModel.setNsPrefix("bot", "https://w3id.org/bot#");
		rdfModel.setNsPrefix("beo", "https://pi.pauwel.be/voc/buildingelement#");
		rdfModel.setNsPrefix("props", "https://w3id.org/props#");

		Iterator<IfcProject> projectIterator = ifcModel.getAllE(IfcProject.class).iterator();
		if(projectIterator.hasNext()){
			IfcProject ifcProject = projectIterator.next();
			//
			//
			Iterator<IfcRelAggregates> projectRelAggregatesIterator = ifcProject.getIsDecomposedBy().iterator();
			if(projectRelAggregatesIterator.hasNext()) {
				List<IfcObjectDefinition> projectRelatedObjectList = projectRelAggregatesIterator.next().getRelatedObjects();
				for(IfcObjectDefinition projectRelatedObject : projectRelatedObjectList) {
					if(projectRelatedObject instanceof IfcSite) {
						IfcSite ifcSite = (IfcSite) projectRelatedObject;
						//
						//
						Resource resSite =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Site_" + ifcSite.getExpressId());
						resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Site"));
						resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Location"));
						resSite.addProperty(RDFS.label, ResourceFactory.createStringLiteral(   ifcSite.getName() != null ? ifcSite.getName().getValue() : "Undefined"  ));
						Iterator<IfcRelAggregates> siteRelAggregatesIterator = ifcSite.getIsDecomposedBy().iterator();
						if(siteRelAggregatesIterator.hasNext()) {
							List<IfcObjectDefinition> siteRelatedObjectList = siteRelAggregatesIterator.next().getRelatedObjects();
							for(IfcObjectDefinition siteRelatedObject : siteRelatedObjectList) {
								if(siteRelatedObject instanceof IfcBuilding) {
									IfcBuilding building = (IfcBuilding) projectRelatedObject;
									parseBuilding(building, resSite);
								}else {
									log.warn("unsupported case");
								}
							}
						}
					}else if (projectRelatedObject instanceof IfcBuilding) {
						IfcBuilding building = (IfcBuilding) projectRelatedObject;
						parseBuilding(building, null);					
					}else {
						log.warn("usupported case");
					}
				}
			}
		}
	}
	
	private void parseBuilding(IfcBuilding building, Resource resSite) {
		Resource resBuilding = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Building_" + building.getExpressId());
		resBuilding.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  building.getName() != null ? building.getName().getValue() : "Undefined"  ));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Building"));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Building"));
		// update parent
		if(resSite != null) {
			resSite.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resBuilding);
			resSite.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasBuilding"), resBuilding);				
		}
		// using containedInSpatialStructure
		Iterator<IfcRelContainedInSpatialStructure> buildingContainedInSpatialStructureIterator = building.getContainsElements().iterator();
		while(buildingContainedInSpatialStructureIterator.hasNext()) {
			IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingContainedInSpatialStructureIterator.next();
			for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
				parseProduct(product, resBuilding);
			}
		}
		// using decomposedBy
		Iterator<IfcRelAggregates> buildingRelAggregatesIterator = building.getIsDecomposedBy().iterator();
		if(buildingRelAggregatesIterator.hasNext()) {
			List<IfcObjectDefinition> buildingRelatedObjectList = buildingRelAggregatesIterator.next().getRelatedObjects();
			for(IfcObjectDefinition builidngRelatedObject : buildingRelatedObjectList) {
				if(builidngRelatedObject instanceof IfcBuildingStorey) {
					IfcBuildingStorey buildingStorey = (IfcBuildingStorey) builidngRelatedObject;
					//
					//
					Resource resStorey = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "BuildingStorey_" + buildingStorey.getExpressId());
					resStorey.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  buildingStorey.getName() != null ? buildingStorey.getName().getValue() : "Undefined"  ));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Storey"));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Floor"));
					resStorey.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resBuilding);									
					// parent resource
					resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasStorey"), resStorey);
					resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resStorey);
					
					// using decomposedBy
					Iterator<IfcRelAggregates> buildingStoreyRelAggregatesIterator = buildingStorey.getIsDecomposedBy().iterator(); 
					if(buildingStoreyRelAggregatesIterator.hasNext()) {
						List<IfcObjectDefinition> buildingStoreyRelatedObjectList = buildingStoreyRelAggregatesIterator.next().getRelatedObjects();
						for(IfcObjectDefinition buildingStoreyRelatedObject : buildingStoreyRelatedObjectList) {
							if(buildingStoreyRelatedObject instanceof IfcProduct) {
								IfcProduct product = (IfcProduct) buildingStoreyRelatedObject;					
								parseProduct(product, resStorey);
							}else {
								// we exclude some unnecessary entities
								log.warn("unsupported case");
							}
						}
					}
					// using containedInSpatialStructure
					Iterator<IfcRelContainedInSpatialStructure> buildingStoreyContainedInSpatialStructureIterator = buildingStorey.getContainsElements().iterator();
					while(buildingStoreyContainedInSpatialStructureIterator.hasNext()) {
						IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingStoreyContainedInSpatialStructureIterator.next();
						for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
							parseProduct(product, resStorey);
						}
					}					
				}else {
					log.warn("unsupported case");
				}
			}
		}
	}
	
	private void parseProduct(IfcProduct product, Resource resParent) {
		if(product instanceof IfcWall) {
			IfcWall ifcWall = (IfcWall) product;
			Resource resWall = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Wall_" + ifcWall.getExpressId());
			resWall.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcWall.getName() != null ? ifcWall.getName().getValue() : "Undefined" ));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Wall"));
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcWall.getHasOpenings().iterator();
			if(wallOpeningIterator.hasNext()) {
				IfcRelVoidsElement relVoidsElement = wallOpeningIterator.next();
				if(relVoidsElement.getRelatedOpeningElement() instanceof IfcOpeningElement) {
					IfcOpeningElement opening = (IfcOpeningElement) relVoidsElement.getRelatedOpeningElement();
					parseOpening(opening, resWall);
				}else {
					log.warn("unsupported case");
				}
			}
		}else if(product instanceof IfcWallStandardCase) {
			IfcWallStandardCase ifcWallStandardCase = (IfcWallStandardCase) product;
			Resource resWall = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "WallStandardCase_" + ifcWallStandardCase.getExpressId());
			resWall.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcWallStandardCase.getName() != null ? ifcWallStandardCase.getName().getValue() : "Undefined" ));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Wall"));
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcWallStandardCase.getHasOpenings().iterator();
			if(wallOpeningIterator.hasNext()) {
				IfcRelVoidsElement relVoidsElement = wallOpeningIterator.next();
				if(relVoidsElement.getRelatedOpeningElement() instanceof IfcOpeningElement) {
					IfcOpeningElement opening = (IfcOpeningElement) relVoidsElement.getRelatedOpeningElement();
					parseOpening(opening, resWall);
				}else {
					log.warn("unsupported case");
				}
			}
		}else if(product instanceof IfcSlab) {
			IfcSlab ifcSlab = (IfcSlab) product;
			Resource resSlab = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Slab_" + ifcSlab.getExpressId());
			resSlab.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcSlab.getName() != null ? ifcSlab.getName().getValue() : "Undefined" ));
			resSlab.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resSlab.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Wall"));
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcSlab.getHasOpenings().iterator();
			if(wallOpeningIterator.hasNext()) {
				IfcRelVoidsElement relVoidsElement = wallOpeningIterator.next();
				if(relVoidsElement.getRelatedOpeningElement() instanceof IfcOpeningElement) {
					IfcOpeningElement opening = (IfcOpeningElement) relVoidsElement.getRelatedOpeningElement();
					parseOpening(opening, resSlab);
				}else {
					log.warn("unsupported case");
				}
			}
		}else if(product instanceof IfcSpace) {
			IfcSpace ifcSpace = (IfcSpace) product;
			Resource resSpace = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Space_" + ifcSpace.getExpressId());
			resSpace.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcSpace.getName() != null ? ifcSpace.getName().getValue() : "Undefined"  ));
			resSpace.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Space"));
			resSpace.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Room"));
			resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resParent);
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSpace"), resSpace);									
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resSpace);			
			Iterator<IfcRelAssigns> spaceAssignmentIterator = ifcSpace.getHasAssignments().iterator();
			if(spaceAssignmentIterator.hasNext()) {
				IfcRelAssigns relAssigns = spaceAssignmentIterator.next();
				if(relAssigns instanceof IfcRelAssignsToGroup) {
					IfcRelAssignsToGroup relAssignsToGroup = (IfcRelAssignsToGroup) relAssigns;
					if(relAssignsToGroup.getRelatingGroup() instanceof IfcZone) {
						IfcZone ifcZone = (IfcZone) relAssignsToGroup.getRelatingGroup();
						Resource resZone = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Zone_" + ifcZone.getExpressId());
						resZone.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcZone.getName() != null ? ifcZone.getName().getValue() : "Undefined"  ));
						resZone.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Zone"));
					}else {
						log.warn("unsupported case");
					}
				}else {
					log.warn("unsupported case");
				}
			}
		}else if(product instanceof IfcColumn) {
			IfcColumn ifcColumn = (IfcColumn) product;
			Resource resColumn = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Column_" + ifcColumn.getExpressId());
			resColumn.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcColumn.getName() != null ? ifcColumn.getName().getValue() : "Undefined" ));
			resColumn.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resColumn.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Column"));
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resColumn);		
		}else if(product instanceof IfcBeam) {
			IfcBeam ifcBeam = (IfcBeam) product;
			Resource resBeam = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Beam_" + ifcBeam.getExpressId());
			resBeam.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcBeam.getName() != null ? ifcBeam.getName().getValue() : "Undefined" ));
			resBeam.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resBeam.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Beam"));
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resBeam);
		}else if(product instanceof IfcRoof) {
			IfcRoof ifcRoof = (IfcRoof) product;			
			if(ifcRoof.getRepresentation() != null) { // hasGeometry
				Resource resRoof = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Roof_" + ifcRoof.getExpressId());
				resRoof.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcRoof.getName() != null ? ifcRoof.getName().getValue() : "Undefined" ));
				resRoof.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resRoof.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Roof"));
				// update parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resRoof);
				// openings
				Iterator<IfcRelVoidsElement> roofOpeningIterator = ifcRoof.getHasOpenings().iterator();
				if(roofOpeningIterator.hasNext()) {
					IfcRelVoidsElement relVoidsElement = roofOpeningIterator.next();
					if(relVoidsElement.getRelatedOpeningElement() instanceof IfcOpeningElement) {
						IfcOpeningElement opening = (IfcOpeningElement) relVoidsElement.getRelatedOpeningElement();
						parseOpening(opening, resRoof);
					}else {
						log.warn("unsupported case");
					}
				}
			}else { // isDecomposedBy
				Iterator<IfcRelAggregates> roofRelAggregatesIterator = ifcRoof.getIsDecomposedBy().iterator();
				if(roofRelAggregatesIterator.hasNext()) {
					List<IfcObjectDefinition> roofRelatedObjectList = roofRelAggregatesIterator.next().getRelatedObjects();
					for(IfcObjectDefinition roofRelatedObject : roofRelatedObjectList) {
						if(roofRelatedObject instanceof IfcProduct){
							IfcProduct subProduct = (IfcProduct) roofRelatedObject;
							parseProduct(subProduct, resParent);
						}
					}
				}
			}
		}
	}
	
	public void parseOpening(IfcOpeningElement opening, Resource resParent) {
		Iterator<IfcRelFillsElement> relFillsElementIterator = opening.getHasFillings().iterator();
		if(relFillsElementIterator.hasNext()) {
			IfcRelFillsElement relFillsElement = relFillsElementIterator.next();
			if(relFillsElement.getRelatedBuildingElement() instanceof IfcDoor) {
				IfcDoor ifcDoor = (IfcDoor) relFillsElement.getRelatedBuildingElement();
				Resource resDoor = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Door_" + ifcDoor.getExpressId());
				resDoor.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcDoor.getName() != null ? ifcDoor.getName().getValue() : "Undefined"  ));
				resDoor.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resDoor.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Door"));
				// update parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSabElement"), resDoor);													
			}else if(relFillsElement.getRelatedBuildingElement() instanceof IfcWindow) {				
				IfcWindow ifcWindow = (IfcWindow) relFillsElement.getRelatedBuildingElement();
				Resource resWindow = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Window_" + ifcWindow.getExpressId());
				resWindow.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcWindow.getName() != null ? ifcWindow.getName().getValue() : "Undefined"  ));
				resWindow.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resWindow.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Window"));
				// update parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSabElement"), resWindow);													
			}else {
				log.warn("unsupported case");
			}
		}
	}
}