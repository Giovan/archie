package com.nedap.archie.flattener;

import com.nedap.archie.aom.*;
import com.nedap.archie.aom.utils.ArchetypeParsePostProcesser;
import com.nedap.archie.rminfo.ModelInfoLookup;
import com.nedap.archie.rminfo.ReferenceModels;

import static com.nedap.archie.flattener.FlattenerUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Flattener. For single use only, create a new flattener for every flatten-action you want to do!
 *
 * TODO: the parent/child naming is very confusing. Make a new name. Original/specialized? Root/specialized? something else? Check the specs!

 *
 * Created by pieter.bos on 21/10/15.
 */
public class Flattener {

    private final ReferenceModels referenceModels;
    //to be able to store Template Overlays transparently during flattening
    private OverridingArchetypeRepository repository;

    private Archetype parent;
    private Archetype child;

    private Archetype result;
    private boolean createOperationalTemplate = false;
    private boolean removeLanguagesFromMetaData = false;
    private boolean useComplexObjectForArchetypeSlotReplacement = false;

    private String[] languagesToKeep = null;

    private ModelInfoLookup lookup;

    private RulesFlattener rulesFlattener = new RulesFlattener();

    CAttributeFlattener cAttributeFlattener = new CAttributeFlattener(this);
    private TupleFlattener tupleFlattener = new TupleFlattener();

    private OperationalTemplateCreator optCreator = new OperationalTemplateCreator(this);


    public Flattener(ArchetypeRepository repository, ReferenceModels models) {
        this.repository = new OverridingArchetypeRepository(repository);
        this.referenceModels = models;
    }

    public Flattener createOperationalTemplate(boolean makeTemplate) {
        this.createOperationalTemplate = makeTemplate;
        return this;
    }

    /**
     * if this flattener is setup to create operational templates, also set it to remove all languages from the terminology
     * except for the given languages
     * @param languages
     * @return
     */
    public Flattener keepLanguages(String... languages) {
        languagesToKeep = languages;
        return this;
    }

    public Flattener removeLanguagesFromMetadata(boolean remove) {
        this.removeLanguagesFromMetaData = remove;
        return this;
    }

    public Archetype flatten(Archetype toFlatten) {
        if(parent != null) {
            throw new IllegalStateException("You've used this flattener before - single use instance, please create a new one!");
        }

        lookup = referenceModels.getModel(toFlatten);
       // new ReflectionConstraintImposer(lookup).setSingleOrMultiple(toFlatten.getDefinition());
        //validate that we can legally flatten first
        String parentId = toFlatten.getParentArchetypeId();
        if(parentId == null) {
            if(createOperationalTemplate) {
                OperationalTemplate template = optCreator.createOperationalTemplate(toFlatten);
                result = template;
                //make an operational template by just filling complex object proxies and archetype slots
                optCreator.fillSlots(template);
                TerminologyFlattener.filterLanguages(template, removeLanguagesFromMetaData, languagesToKeep);
                result = template;
            } else {
                result = toFlatten.clone();
            }
            result.getDefinition().setArchetype(result);
            return result;
        }

        this.parent = repository.getArchetype(toFlatten.getParentArchetypeId());
        if(parent == null) {
            throw new IllegalArgumentException("parent archetype not found in repository: " + toFlatten.getParentArchetypeId());
        }
        this.child = toFlatten.clone();//just to be sure, so we don't have to copy more things deeper down


        if(child instanceof Template) {
            Template childTemplate = (Template) child;
            for(TemplateOverlay overlay:childTemplate.getTemplateOverlays()) {
                //we'll flatten them later when we need them, otherwise, you run into problems with archetypes
                //not yet added to repository while we already need them
                repository.addArchetype(overlay);
            }
        }

        if(parent.getParentArchetypeId() != null) {
            //parent needs flattening first
            parent = getNewFlattener().flatten(parent);
        }


        this.result = null;
        if(createOperationalTemplate) {
            result = optCreator.createOperationalTemplate(parent);
            optCreator.overrideArchetypeId(result, child);
        } else {
            result = parent.clone();
        }

        //1. redefine structure
        //2. fill archetype slots if we are creating an operational template
        flattenDefinition(result, child);
        if(createOperationalTemplate) {
            optCreator.removeZeroOccurrencesConstraints(result);
        } else {
            prohibitZeroOccurrencesConstraints(result);
        }

        rulesFlattener.combineRules(child, result, "prefix", "", "", true /* override statements with same tag */);//TODO: actually set a unique prefix
        if(createOperationalTemplate) {
            optCreator.fillSlots((OperationalTemplate) result);

        }
        TerminologyFlattener.flattenTerminology(result, child);

        if(createOperationalTemplate) {
            TerminologyFlattener.filterLanguages((OperationalTemplate) result, removeLanguagesFromMetaData, languagesToKeep);
        }
        result.getDefinition().setArchetype(result);
        result.setDifferential(false);//note this archetype as being flat
        if(!createOperationalTemplate) {
            //set metadata to specialized archetype
            result.setOriginalLanguage(child.getOriginalLanguage());
            result.setDescription(child.getDescription());
            result.setOtherMetaData(child.getOtherMetaData());
            result.setGenerated(child.getGenerated());
            result.setControlled(child.getControlled());
            result.setBuildUid(child.getBuildUid());
            result.setTranslations(child.getTranslations());
        } //else as well, but is done elsewhere. needs refactor.
        ArchetypeParsePostProcesser.fixArchetype(result);
        return result;
    }

    /** Zero occurrences and existence constraint processing when flattening. Does not remove attributes*/
    private void prohibitZeroOccurrencesConstraints(Archetype archetype) {
        Stack<CObject> workList = new Stack<>();
        workList.push(archetype.getDefinition());
        while(!workList.isEmpty()) {
            CObject object = workList.pop();
            for(CAttribute attribute:object.getAttributes()) {
                if(attribute.getExistence() != null && attribute.getExistence().getUpper() == 0 && !attribute.getExistence().isUpperUnbounded()) {
                    //remove children, but do not remove attribute itself to make sure it stays prohibited
                    attribute.setChildren(new ArrayList<>());
                } else {
                    List<CObject> objectsToRemove = new ArrayList<>();
                    for (CObject child : attribute.getChildren()) {
                        if (!child.isAllowed()) {
                            objectsToRemove.add(child);
                        }
                        workList.push(child);
                    }
                    attribute.getChildren().removeAll(objectsToRemove);
                }

            }

        }
    }

    private void flattenDefinition(Archetype parent, Archetype specialized) {
        parent.setArchetypeId(specialized.getArchetypeId()); //TODO: override all metadata?
        createSpecializeCObject(null, parent.getDefinition(), specialized.getDefinition());

    }


    protected CObject createSpecializeCObject(CAttribute attribute, CObject parent, CObject specialized) {
        if(parent == null) {
            return specialized;//TODO: clone?
        }
        CObject newObject = cloneSpecializedObject(attribute, parent, specialized);

        specializeOccurrences(specialized, newObject);
        newObject.setSiblingOrder(getPossiblyOverridenValue(newObject.getSiblingOrder(), specialized.getSiblingOrder()));

        newObject.setNodeId(getPossiblyOverridenValue(newObject.getNodeId(), specialized.getNodeId()));
        newObject.setRmTypeName(getPossiblyOverridenValue(newObject.getRmTypeName(), specialized.getRmTypeName()));

        //now specialize the structure under the specialized node
        specializeContent(parent, specialized, newObject);
        return newObject;
    }

    private void specializeContent(CObject parent, CObject specialized, CObject newObject) {

        if (parent instanceof CComplexObject) {
            if(((CComplexObject) parent).isAnyAllowed() && specialized instanceof CComplexObjectProxy) {
                //you can replace an any allowed node with a CComplexObjectProxy. No content will need to be specialized, just merge it in
            }
            else if(!(specialized instanceof CComplexObject)) {
                //this is the specs. The ADL workbench allows an ARCHETYPE_SLOT to override a C_ARCHETYPE_ROOT without errors. Filed as https://openehr.atlassian.net/projects/AWBPR/issues/AWBPR-72
                throw new IllegalArgumentException(String.format("cannot override complex object %s (%s) with non-complex object %s (%s)", parent.path(), parent.getClass().getSimpleName(), specialized.path(), specialized.getClass().getSimpleName()));
            } else {
                flattenCComplexObject((CComplexObject) newObject, (CComplexObject) specialized);
            }
        }
        else if (newObject instanceof ArchetypeSlot) {//archetypeslot is NOT a complex object. It's replacement can be
            if(specialized instanceof ArchetypeSlot) {
                flattenArchetypeSlot((ArchetypeSlot) newObject, (ArchetypeSlot) specialized);
            } else if(specialized instanceof CArchetypeRoot) {
                //TODO: handle as if this is a template overlay, but inline. Probably needed in the fillArchetypeRoot method, not here?
            } else {
                throw new IllegalArgumentException("Can only replace an archetype slot with an archetype root or another archetype slot, not with a " + newObject.getClass());
            }
        }
    }

    private void specializeOccurrences(CObject specialized, CObject newObject) {
        //TODO: check if overriding occurrences is allowed
        newObject.setOccurrences(getPossiblyOverridenValue(newObject.getOccurrences(), specialized.getOccurrences()));
    }

    private CObject cloneSpecializedObject(CAttribute attribute, CObject parent, CObject specialized) {
        CObject newObject;
        if(attribute == null) {
            //root of archetype. don't clone anything.. alternative: make a mock attribute at the root
            newObject = parent;
        } else {
            newObject = (CObject) parent.clone();
        }
        if(newObject instanceof ArchetypeSlot && specialized instanceof CArchetypeRoot) {
            newObject = (CObject) specialized.clone();
        }
        return newObject;
    }





    private void flattenArchetypeSlot(ArchetypeSlot parent, ArchetypeSlot specialized) {
        if(specialized.isClosed()) {
            parent.setClosed(true);
        }
        parent.setIncludes(getPossiblyOverridenListValue(parent.getIncludes(), specialized.getIncludes()));
        parent.setExcludes(getPossiblyOverridenListValue(parent.getExcludes(), specialized.getExcludes()));

        //TODO: includes/excludes?
    }

    /**
     * Flatten a CComplexObject. newObject must be a clone of the original parent, specialized the original unmodified
     * specialized node.
     *
     * The attributes of newObject will be changed in place, so newObject will be altered in this operation
     *
     * @param newObject
     * @param specialized
     */
    private void flattenCComplexObject(CComplexObject newObject, CComplexObject specialized) {

        if(specialized instanceof CArchetypeRoot && newObject instanceof CArchetypeRoot) {
            //cloneSpecializedObject() will already have handled the case where the parent is an ARCHETYPE_SLOT
            //and the child is a C_ARCHETYPE_ROOT by cloning the child instead of the parent
            //handle redefinition of CArchetypeRoots here.
            CArchetypeRoot specializedArchetypeRoot = (CArchetypeRoot) specialized;
            if(specializedArchetypeRoot.getArchetypeRef() != null) {
                CArchetypeRoot newArchetypeRoot = (CArchetypeRoot) newObject;
                newArchetypeRoot.setArchetypeRef(specializedArchetypeRoot.getArchetypeRef());
            }
        }

        for(CAttribute attribute:specialized.getAttributes()) {
            cAttributeFlattener.flattenSingleAttribute(newObject, attribute);
        }
        for(CAttributeTuple tuple:specialized.getAttributeTuples()) {
            tupleFlattener.flattenTuple(newObject, tuple);
        }
    }

    protected Flattener getNewFlattener() {
        return new Flattener(repository, referenceModels)
                .createOperationalTemplate(false) //do not create operational template except at the end.
                .useComplexObjectForArchetypeSlotReplacement(useComplexObjectForArchetypeSlotReplacement);
    }

    private Flattener useComplexObjectForArchetypeSlotReplacement(boolean useComplexObjectForArchetypeSlotReplacement) {
        this.useComplexObjectForArchetypeSlotReplacement = useComplexObjectForArchetypeSlotReplacement;
        return this;
    }

    public boolean isUseComplexObjectForArchetypeSlotReplacement() {
        return useComplexObjectForArchetypeSlotReplacement;
    }

    protected ModelInfoLookup getLookup() {
        return lookup;
    }


    public boolean getCreateOperationalTemplate() {
        return createOperationalTemplate;
    }

    protected RulesFlattener getRulesFlattener() {
        return rulesFlattener;
    }

    public OverridingArchetypeRepository getRepository() {
        return repository;
    }
}
