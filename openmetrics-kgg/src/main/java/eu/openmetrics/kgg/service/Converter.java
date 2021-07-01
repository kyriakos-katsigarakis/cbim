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
		rdfModel.setNsPrefix("props", "#");

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
						String siteName = "undefined";
						if(ifcSite.getName() != null) {
							siteName = ifcSite.getName().getValue(); 
						}
						Resource resSite =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + siteName);
						resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Site"));
						resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Location"));
						resSite.addProperty(RDFS.label, ResourceFactory.createStringLiteral( siteName ));
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
		String buildingName = "undefined";
		if(building.getName() != null) {
			buildingName = building.getName().getValue();
		}
		String siteName = "undefined";
		if(resSite != null) {
			siteName = resSite.getLocalName();
		}
		Resource resBuilding = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + siteName + "_" + buildingName);
		resBuilding.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(buildingName));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Building"));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Building"));
		// parent resource
		if(resSite != null) {
			resSite.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resBuilding);
			resSite.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasBuilding"), resBuilding);				
		}
		// 1. using containedInSpatialStructure
		Iterator<IfcRelContainedInSpatialStructure> buildingContainedInSpatialStructureIterator = building.getContainsElements().iterator();
		while(buildingContainedInSpatialStructureIterator.hasNext()) {
			IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingContainedInSpatialStructureIterator.next();
			for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
				// -->
				parseProduct(product, resBuilding);
			}
		}
		// 2. using decomposedBy
		Iterator<IfcRelAggregates> buildingRelAggregatesIterator = building.getIsDecomposedBy().iterator();
		if(buildingRelAggregatesIterator.hasNext()) {
			List<IfcObjectDefinition> buildingRelatedObjectList = buildingRelAggregatesIterator.next().getRelatedObjects();
			for(IfcObjectDefinition builidngRelatedObject : buildingRelatedObjectList) {
				if(builidngRelatedObject instanceof IfcBuildingStorey) {
					IfcBuildingStorey buildingStorey = (IfcBuildingStorey) builidngRelatedObject;
					//
					//
					String buildingStoreyName = "undefined";
					if(buildingStorey.getName() != null) {
						buildingStoreyName = buildingStorey.getName().getValue();
					}
					Resource resStorey = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + siteName + "_" + buildingName + "_" + buildingStoreyName);
					resStorey.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( buildingStoreyName ));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Storey"));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Floor"));
					resStorey.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resBuilding);									
					// parent resource
					resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasStorey"), resStorey);
					resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resStorey);
					
					// 1. using decomposedBy
					Iterator<IfcRelAggregates> buildingStoreyRelAggregatesIterator = buildingStorey.getIsDecomposedBy().iterator(); 
					if(buildingStoreyRelAggregatesIterator.hasNext()) {
						List<IfcObjectDefinition> buildingStoreyRelatedObjectList = buildingStoreyRelAggregatesIterator.next().getRelatedObjects();
						for(IfcObjectDefinition buildingStoreyRelatedObject : buildingStoreyRelatedObjectList) {
							if(buildingStoreyRelatedObject instanceof IfcProduct) {
								IfcProduct product = (IfcProduct) buildingStoreyRelatedObject;
								// -->						
								parseProduct(product, resStorey);
							}else {
								// we exclude some entities
								log.warn("unsupported case");
							}
						}
					}
					// 2. using containedInSpatialStructure
					Iterator<IfcRelContainedInSpatialStructure> buildingStoreyContainedInSpatialStructureIterator = buildingStorey.getContainsElements().iterator();
					while(buildingStoreyContainedInSpatialStructureIterator.hasNext()) {
						IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingStoreyContainedInSpatialStructureIterator.next();
						for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
							// -->
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
			//
			//
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcWall.getHasOpenings().iterator();
			if(wallOpeningIterator.hasNext()) {
				IfcRelVoidsElement relVoidsElement = wallOpeningIterator.next();
				if(relVoidsElement.getRelatedOpeningElement() instanceof IfcOpeningElement) {
					IfcOpeningElement opening = (IfcOpeningElement) relVoidsElement.getRelatedOpeningElement();
					// -->
					parseOpening(opening);
				}else {
					log.warn("unsupported case");
				}
			}
		}else if(product instanceof IfcWallStandardCase) {
			IfcWallStandardCase ifcWallStandardCase = (IfcWallStandardCase) product;
			//
			//
			
		}else if(product instanceof IfcSlab) {
			IfcSlab ifcSlab = (IfcSlab) product;
			//
			//
			
		}else if(product instanceof IfcSpace) {
			IfcSpace ifcSpace = (IfcSpace) product;
			//
			//
			String spaceName = "undefined";
			if(ifcSpace.getName() != null) {
				spaceName = ifcSpace.getName().getValue(); 
			}
			Resource resSpace = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + resParent.getLocalName() + "_" + spaceName);
			resSpace.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Space"));
			resSpace.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Room"));
			resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPartOf"), resParent);
			resSpace.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(spaceName));
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSpace"), resSpace);									
			//?			
			// update parent
			resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resSpace);
			//?
		}else if(product instanceof IfcColumn) {
			IfcColumn ifcColumn = (IfcColumn) product;
			//
			//
				
		}else if(product instanceof IfcBeam) {
			IfcBeam ifcBeam = (IfcBeam) product;
			//
			//
			
		}else if(product instanceof IfcRoof) {
			IfcRoof ifcRoof = (IfcRoof) product;			
			//
			//
			
			// 1. hasOpenings
			
			
			// 2. isDecomposedBy (e.g. slabs)
		}
	}
	
	public void parseOpening(IfcOpeningElement opening) {
		Iterator<IfcRelFillsElement> relFillsElementIterator = opening.getHasFillings().iterator();
		if(relFillsElementIterator.hasNext()) {
			IfcRelFillsElement relFillsElement = relFillsElementIterator.next();
			if(relFillsElement.getRelatedBuildingElement() instanceof IfcDoor) {
				IfcDoor door = (IfcDoor) relFillsElement.getRelatedBuildingElement();
				//
				//
				
			}else if(relFillsElement.getRelatedBuildingElement() instanceof IfcWindow) {				
				IfcWindow window = (IfcWindow) relFillsElement.getRelatedBuildingElement();
				//
				//
				
			}else {
				log.warn("unsupported case");
			}
		}
	}
}
