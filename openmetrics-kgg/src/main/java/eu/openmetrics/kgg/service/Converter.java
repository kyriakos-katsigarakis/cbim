package eu.openmetrics.kgg.service;

import java.util.Iterator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gr.tuc.ifc.IfcModel;
import gr.tuc.ifc4.IfcAreaMeasure;
import gr.tuc.ifc4.IfcBeam;
import gr.tuc.ifc4.IfcBuilding;
import gr.tuc.ifc4.IfcBuildingStorey;
import gr.tuc.ifc4.IfcColumn;
import gr.tuc.ifc4.IfcDoor;
import gr.tuc.ifc4.IfcElement;
import gr.tuc.ifc4.IfcIdentifier;
import gr.tuc.ifc4.IfcObjectDefinition;
import gr.tuc.ifc4.IfcOpeningElement;
import gr.tuc.ifc4.IfcProduct;
import gr.tuc.ifc4.IfcProject;
import gr.tuc.ifc4.IfcProperty;
import gr.tuc.ifc4.IfcPropertySet;
import gr.tuc.ifc4.IfcPropertySingleValue;
import gr.tuc.ifc4.IfcReal;
import gr.tuc.ifc4.IfcRelAggregates;
import gr.tuc.ifc4.IfcRelAssigns;
import gr.tuc.ifc4.IfcRelAssignsToGroup;
import gr.tuc.ifc4.IfcRelContainedInSpatialStructure;
import gr.tuc.ifc4.IfcRelDefinesByProperties;
import gr.tuc.ifc4.IfcRelFillsElement;
import gr.tuc.ifc4.IfcRelSpaceBoundary;
import gr.tuc.ifc4.IfcRelSpaceBoundary2ndLevel;
import gr.tuc.ifc4.IfcRelVoidsElement;
import gr.tuc.ifc4.IfcRoof;
import gr.tuc.ifc4.IfcSite;
import gr.tuc.ifc4.IfcSlab;
import gr.tuc.ifc4.IfcSpace;
import gr.tuc.ifc4.IfcSpaceBoundarySelect;
import gr.tuc.ifc4.IfcUnitaryControlElement;
import gr.tuc.ifc4.IfcUnitaryControlElementTypeEnum;
import gr.tuc.ifc4.IfcUnitaryEquipment;
import gr.tuc.ifc4.IfcUnitaryEquipmentTypeEnum;
import gr.tuc.ifc4.IfcValue;
import gr.tuc.ifc4.IfcVolumeMeasure;
import gr.tuc.ifc4.IfcWall;
import gr.tuc.ifc4.IfcWallStandardCase;
import gr.tuc.ifc4.IfcWindow;
import gr.tuc.ifc4.IfcZone;

@Service
public class Converter {

	private static Logger log = LoggerFactory.getLogger(Converter.class);
	
	private Model rdfModel;
	
	public Converter() {
		log.info("Knowledge Graph Generator");
	}

	public Model convert(IfcModel ifcModel) {
		rdfModel = ModelFactory.createDefaultModel();
		rdfModel.setNsPrefix("owl", OWL.getURI());
		rdfModel.setNsPrefix("rdf", RDF.getURI());
		rdfModel.setNsPrefix("rdfs", RDFS.getURI());
		rdfModel.setNsPrefix("xsd", XSD.getURI());
		rdfModel.setNsPrefix("schema", "http://schema.org#");
		rdfModel.setNsPrefix("om", "http://openmetrics.eu/openmetrics#");
		rdfModel.setNsPrefix("brick", "https://brickschema.org/schema/1.1/Brick#");
		rdfModel.setNsPrefix("bot", "https://w3id.org/bot#");
		rdfModel.setNsPrefix("beo", "https://pi.pauwel.be/voc/buildingelement#");
		rdfModel.setNsPrefix("props", "https://w3id.org/props#");
		Iterator<IfcProject> projectIterator = ifcModel.getAllE(IfcProject.class).iterator();
		if(projectIterator.hasNext()) {
			IfcProject ifcProject = projectIterator.next();
			parseProject(ifcProject);
		}
		return rdfModel;
	}
	
	private void parseProject(IfcProject ifcProject) {
		Iterator<IfcRelAggregates> projectRelAggregatesIterator = ifcProject.getIsDecomposedBy().iterator();
		while(projectRelAggregatesIterator.hasNext()) {
			IfcRelAggregates projectRelAggregates = projectRelAggregatesIterator.next();
			List<IfcObjectDefinition> projectRelatedObjectList = projectRelAggregates.getRelatedObjects();
			for(IfcObjectDefinition projectRelatedObject : projectRelatedObjectList) {
				if(projectRelatedObject instanceof IfcSite) {
					IfcSite ifcSite = (IfcSite) projectRelatedObject;
					Resource resSite =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Site_" + ifcSite.getExpressId());
					resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Site"));
					resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Location"));
					resSite.addProperty(RDFS.label, ResourceFactory.createStringLiteral(   ifcSite.getName() != null ? ifcSite.getName().getValue() : "Undefined"  ));
					resSite.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcSite.getGlobalId().getValue() ));
					Iterator<IfcRelAggregates> siteRelAggregatesIterator = ifcSite.getIsDecomposedBy().iterator();
					while(siteRelAggregatesIterator.hasNext()) {
						IfcRelAggregates siteRelAggregates = siteRelAggregatesIterator.next();
						List<IfcObjectDefinition> siteRelatedObjectList = siteRelAggregates.getRelatedObjects();
						for(IfcObjectDefinition siteRelatedObject : siteRelatedObjectList) {
							if(siteRelatedObject instanceof IfcBuilding) {
								IfcBuilding building = (IfcBuilding) siteRelatedObject;
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
	
	private void parseBuilding(IfcBuilding building, Resource resSite) {
		Resource resBuilding = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Building_" + building.getExpressId());
		resBuilding.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  building.getName() != null ? building.getName().getValue() : "Undefined"  ));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Building"));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Building"));
		resBuilding.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( building.getGlobalId().getValue() ));
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
		while(buildingRelAggregatesIterator.hasNext()) {
			IfcRelAggregates buildingRelAggregates = buildingRelAggregatesIterator.next();
			List<IfcObjectDefinition> buildingRelatedObjectList = buildingRelAggregates.getRelatedObjects();
			for(IfcObjectDefinition builidngRelatedObject : buildingRelatedObjectList) {
				if(builidngRelatedObject instanceof IfcBuildingStorey) {
					IfcBuildingStorey buildingStorey = (IfcBuildingStorey) builidngRelatedObject;
					//
					Resource resStorey = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "BuildingStorey_" + buildingStorey.getExpressId());
					resStorey.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  buildingStorey.getName() != null ? buildingStorey.getName().getValue() : "Undefined"  ));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Storey"));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Floor"));
					resStorey.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resBuilding);
					resStorey.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( buildingStorey.getGlobalId().getValue() ));
					// parent resource
					resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasStorey"), resStorey);
					resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resStorey);
					// using decomposedBy
					Iterator<IfcRelAggregates> buildingStoreyRelAggregatesIterator = buildingStorey.getIsDecomposedBy().iterator(); 
					while(buildingStoreyRelAggregatesIterator.hasNext()) {
						IfcRelAggregates buildingStoreyRelAggregates = buildingStoreyRelAggregatesIterator.next();
						List<IfcObjectDefinition> buildingStoreyRelatedObjectList = buildingStoreyRelAggregates.getRelatedObjects();
						for(IfcObjectDefinition buildingStoreyRelatedObject : buildingStoreyRelatedObjectList) {
							if(buildingStoreyRelatedObject instanceof IfcProduct) {
								IfcProduct product = (IfcProduct) buildingStoreyRelatedObject;					
								parseProduct(product, resStorey);
							}else {
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
			Resource resWall = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcWall.getExpressId());
			resWall.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcWall.getName() != null ? ifcWall.getName().getValue() : "Undefined" ));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Wall"));
			resWall.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcWall.getGlobalId().getValue() ));
			// upd parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resWall);
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcWall.getHasOpenings().iterator();
			while(wallOpeningIterator.hasNext()) {
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
			Resource resWall = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcWallStandardCase.getExpressId());
			resWall.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcWallStandardCase.getName() != null ? ifcWallStandardCase.getName().getValue() : "Undefined" ));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resWall.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Wall"));
			resWall.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcWallStandardCase.getGlobalId().getValue() ));
			// upd parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resWall);
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcWallStandardCase.getHasOpenings().iterator();
			while(wallOpeningIterator.hasNext()) {
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
			Resource resSlab = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcSlab.getExpressId());
			resSlab.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcSlab.getName() != null ? ifcSlab.getName().getValue() : "Undefined" ));
			resSlab.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resSlab.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Wall"));
			resSlab.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcSlab.getGlobalId().getValue() ));
			// upd parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resSlab);
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcSlab.getHasOpenings().iterator();
			while(wallOpeningIterator.hasNext()) {
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
			resSpace.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Space"));
			resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resParent);
			resSpace.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcSpace.getGlobalId().getValue() ));
			// upd parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSpace"), resSpace);									
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resSpace);			
			// properties
			parsePsets(ifcSpace, resSpace);
			// zones
			Iterator<IfcRelAssigns> spaceAssignmentIterator = ifcSpace.getHasAssignments().iterator();
			while(spaceAssignmentIterator.hasNext()) {
				IfcRelAssigns relAssigns = spaceAssignmentIterator.next();
				if(relAssigns instanceof IfcRelAssignsToGroup) {
					IfcRelAssignsToGroup relAssignsToGroup = (IfcRelAssignsToGroup) relAssigns;
					if(relAssignsToGroup.getRelatingGroup() instanceof IfcZone) {
						IfcZone ifcZone = (IfcZone) relAssignsToGroup.getRelatingGroup();
						Resource resZone = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Zone_" + ifcZone.getExpressId());
						resZone.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcZone.getName() != null ? ifcZone.getName().getValue() : "Undefined"  ));
						resZone.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Zone"));
						resZone.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resSpace);
						// upd space
						resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resZone);
					}else {
						log.warn("unsupported case");
					}
				}else {
					log.warn("unsupported case");
				}
			}
			// space boundaries
			for(IfcRelSpaceBoundary relSpaceBoundary : ifcSpace.getBoundedBy()) {
				if(relSpaceBoundary instanceof IfcRelSpaceBoundary2ndLevel) {
					IfcRelSpaceBoundary2ndLevel relSpaceBoundary2ndLevel = (IfcRelSpaceBoundary2ndLevel) relSpaceBoundary;
					IfcElement ifcElement = relSpaceBoundary2ndLevel.getRelatedBuildingElement();
					if(ifcElement != null) {
						Resource resElement = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcElement.getExpressId());
						resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "adjacentElement"), resElement);						
					}
					IfcRelSpaceBoundary2ndLevel correspondingRelSpaceBoundary2ndLevel = relSpaceBoundary2ndLevel.getCorrespondingBoundary();
					if(correspondingRelSpaceBoundary2ndLevel != null) {
						IfcSpaceBoundarySelect spaceBoundarySelect = correspondingRelSpaceBoundary2ndLevel.getRelatingSpace();
						if(spaceBoundarySelect instanceof IfcSpace) {
							IfcSpace ifcCorrespondingSpace = (IfcSpace) spaceBoundarySelect;
							Resource resCorrespondingSpace = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Space_" + ifcCorrespondingSpace.getExpressId());
							// update adjacent spaces
							resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "adjacentZone"), resCorrespondingSpace);
							resCorrespondingSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "adjacentZone"), resSpace);							
						}
					}
				}
			}
			// products included in the space
			Iterator<IfcRelContainedInSpatialStructure> buildingContainedInSpatialStructureIterator = ifcSpace.getContainsElements().iterator();
			while(buildingContainedInSpatialStructureIterator.hasNext()) {
				IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingContainedInSpatialStructureIterator.next();
				for(IfcProduct spaceProduct : relContainedInSpatialStructure.getRelatedElements()) {					
					parseProduct(spaceProduct, resSpace);
				}
			}	
		}else if(product instanceof IfcColumn) {
			IfcColumn ifcColumn = (IfcColumn) product;
			Resource resColumn = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcColumn.getExpressId());
			resColumn.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcColumn.getName() != null ? ifcColumn.getName().getValue() : "Undefined" ));
			resColumn.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resColumn.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Column"));
			resColumn.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcColumn.getGlobalId().getValue() ));
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resColumn);		
		}else if(product instanceof IfcBeam) {
			IfcBeam ifcBeam = (IfcBeam) product;
			Resource resBeam = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcBeam.getExpressId());
			resBeam.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcBeam.getName() != null ? ifcBeam.getName().getValue() : "Undefined" ));
			resBeam.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
			resBeam.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Beam"));
			resBeam.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcBeam.getGlobalId().getValue() ));
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resBeam);
		}else if(product instanceof IfcRoof) {
			IfcRoof ifcRoof = (IfcRoof) product;
			if(ifcRoof.getRepresentation() != null) { // hasGeometry
				Resource resRoof = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcRoof.getExpressId());
				resRoof.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcRoof.getName() != null ? ifcRoof.getName().getValue() : "Undefined" ));
				resRoof.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resRoof.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Roof"));
				resRoof.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcRoof.getGlobalId().getValue() ));
				// upd parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resRoof);
				// openings
				Iterator<IfcRelVoidsElement> roofOpeningIterator = ifcRoof.getHasOpenings().iterator();
				while(roofOpeningIterator.hasNext()) {
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
				while(roofRelAggregatesIterator.hasNext()) {
					IfcRelAggregates roofRelAggregates = roofRelAggregatesIterator.next();
					List<IfcObjectDefinition> roofRelatedObjectList = roofRelAggregates.getRelatedObjects();
					for(IfcObjectDefinition roofRelatedObject : roofRelatedObjectList) {
						if(roofRelatedObject instanceof IfcProduct){
							IfcProduct subProduct = (IfcProduct) roofRelatedObject;
							parseProduct(subProduct, resParent);
						}
					}
				}
			}
		}else if(product instanceof IfcUnitaryEquipment) {
			IfcUnitaryEquipment ifcUnitaryEquipment = (IfcUnitaryEquipment) product;
			if(ifcUnitaryEquipment.getPredefinedType().equals(IfcUnitaryEquipmentTypeEnum.SPLITSYSTEM)) {
				Resource resSplitSystem = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcUnitaryEquipment.getExpressId() );
				resSplitSystem.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcUnitaryEquipment.getName() != null ? ifcUnitaryEquipment.getName().getValue() : "Undefined" ));
				resSplitSystem.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resSplitSystem.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Terminal_Unit"));
				resSplitSystem.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcUnitaryEquipment.getGlobalId().getValue() ));
				// upd parent
				resSplitSystem.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resParent);
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resSplitSystem);			
				// query
				NodeIterator nodeIterator = rdfModel.listObjectsOfProperty(resParent, ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf") );
				if(nodeIterator.hasNext()) {
					RDFNode rdfNode = nodeIterator.next();
					Resource resource = rdfNode.asResource();
					if(resource.hasProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Zone"))) {						
						resSplitSystem.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "feeds"), resource);
						resource.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isFedBy"), resSplitSystem);
					}
				}
				// add psets
				parsePsets(ifcUnitaryEquipment, resSplitSystem);
			}else {
				log.warn("unsupported case");
			}
		}else if(product instanceof IfcUnitaryControlElement) {
			IfcUnitaryControlElement ifcUnitaryControlElement = (IfcUnitaryControlElement) product;		
			if(ifcUnitaryControlElement.getPredefinedType().equals(IfcUnitaryControlElementTypeEnum.THERMOSTAT)) {
				Resource resControlPanel = rdfModel.createResource( rdfModel.getNsPrefixURI("om") + "Element_" + ifcUnitaryControlElement.getExpressId() );
				resControlPanel.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcUnitaryControlElement.getName() != null ? ifcUnitaryControlElement.getName().getValue() : "Undefined" ));
				resControlPanel.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resControlPanel.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Thermostat"));
				resControlPanel.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcUnitaryControlElement.getGlobalId().getValue() ));
				// upd parent
				resControlPanel.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resParent);
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "containsElement"), resControlPanel);
				// add psets
				parsePsets(ifcUnitaryControlElement, resControlPanel);
			}else {
				log.warn("unsupported case");
			}
		}
	}

	public void parseOpening(IfcOpeningElement opening, Resource resParent) {
		Iterator<IfcRelFillsElement> relFillsElementIterator = opening.getHasFillings().iterator();
		while(relFillsElementIterator.hasNext()) {
			IfcRelFillsElement relFillsElement = relFillsElementIterator.next();
			if(relFillsElement.getRelatedBuildingElement() instanceof IfcDoor) {
				IfcDoor ifcDoor = (IfcDoor) relFillsElement.getRelatedBuildingElement();
				Resource resDoor = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcDoor.getExpressId());
				resDoor.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcDoor.getName() != null ? ifcDoor.getName().getValue() : "Undefined"  ));
				resDoor.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resDoor.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Door"));	
				resDoor.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcDoor.getGlobalId().getValue() ));
				parsePsets(ifcDoor, resDoor);
				// upd parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSubElement"), resDoor);													
			}else if(relFillsElement.getRelatedBuildingElement() instanceof IfcWindow) {				
				IfcWindow ifcWindow = (IfcWindow) relFillsElement.getRelatedBuildingElement();
				Resource resWindow = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcWindow.getExpressId());
				resWindow.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcWindow.getName() != null ? ifcWindow.getName().getValue() : "Undefined"  ));
				resWindow.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resWindow.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Window"));
				resWindow.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcWindow.getGlobalId().getValue() ));
				parsePsets(ifcWindow, resWindow);
				// upd parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSubElement"), resWindow);													
			}else {
				log.warn("unsupported case");
			}
		}
	}

	void parsePsets(IfcProduct ifcProduct, Resource resProduct) {
		Iterator<IfcRelDefinesByProperties> windowRelDefinesByProperties = ifcProduct.getIsDefinedBy().iterator();
		while(windowRelDefinesByProperties.hasNext()) {
			IfcRelDefinesByProperties relDefinesByProperties = windowRelDefinesByProperties.next();
			if(relDefinesByProperties.getRelatingPropertyDefinition() instanceof IfcPropertySet) {
				IfcPropertySet propertySet = (IfcPropertySet) relDefinesByProperties.getRelatingPropertyDefinition();
				Iterator<IfcProperty> propertiesIterator = propertySet.getHasProperties().iterator();
				while(propertiesIterator.hasNext()) {
					IfcProperty ifcProperty = propertiesIterator.next();
					if(ifcProperty instanceof IfcPropertySingleValue) {
						IfcPropertySingleValue propertySignleValue = (IfcPropertySingleValue) ifcProperty;
						if(propertySignleValue.getName().getValue().equalsIgnoreCase("Ufactor")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "heatTransferCoefficientU"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}									
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("SolarHeatGainCoefficient")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "solarHeatGainCoefficient"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("VisibleTransmittance")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "visualLightTransmittance"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("Area")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcAreaMeasure) {
								IfcAreaMeasure ifcMeasure = (IfcAreaMeasure) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasArea"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("Volume")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcVolumeMeasure) {
								IfcVolumeMeasure ifcMeasure = (IfcVolumeMeasure) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasVolume"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("SerialNumber")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcIdentifier) {
								IfcIdentifier ifcIdentifier = (IfcIdentifier) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasSerialNumber"),  ResourceFactory.createStringLiteral(ifcIdentifier.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("COP")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCOP"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("EER")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasEER"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}
					}
				}
			}else {
				log.warn("unsupported case");
			}
		}
	}
}