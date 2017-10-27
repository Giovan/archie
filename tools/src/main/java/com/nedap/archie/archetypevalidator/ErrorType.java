package com.nedap.archie.archetypevalidator;

/**
 * Created by pieter.bos on 31/03/2017.
 */
public enum ErrorType {
    VCOSU("object node identifier validity: every object node must be unique within the archetype"),
    VCOID("object node identifier validity: every object node must have a node identifier"),
    VCORM("object constraint type name existence: a type name introducing an object constraint block must be defined in the underlying information model"),
    VCORMT("object constraint type validity: a type name introducing an object constraint block must be the same as or conform to the type stated in the underlying information model of its owning attribute"),
    VCARM("attribute name reference model validity: an attribute name introducing an attribute constraint block must be defined in the underlying information model as an attribute (stored or computed) of the type which introduces the enclosing object block"),
    VCAEX("archetype attribute reference model existence conformance: the existence of an attribute, if set, must conform, i.e. be the same or narrower, to the existence of the corresponding attribute in the underlying information model"),
    VCAM("archetype attribute reference model multiplicity conformance: the multiplicity, i.e. whether an attribute is multiply- or single-valued, of an attribute must conform to that of the corresponding attribute in the underlying information model"),
    VCATU("attribute uniqueness: sibling attributes occurring within an object node must be uniquely named with respect to each other, in the same way as for class definitions in an object reference model"),
    //terminology validity rules
    VTVSID("value-set id defined. The identifying code of a value set must be defined in the term definitions of the terminology of the current archetype"),
    VTVSMD("value-set members defined. The member codes of a value set must be defined in the term definitions of the terminology of the flattened form of the current archetype"),
    VTLC("language consistency. Languages consistent: all term codes and constraint codes exist in all languages"),
    OTHER("an error occurred that has no standard codes"),
    VARDT("archetype definition typename validity. The typename mentioned in the outer block of the archetype definition section must match the type mentioned in the first segment of the archetype id"),
    STCNT("Syntax error: terminology not specified"),
    VOLT("Original language not defined in terminology"),
    VOTM("terminology translations validity. Translations must exist for term_definitions and constraint_definitions sections for all languages defined in the description / translations sections."),
    VACSD("The specialisation depth of the archetypes must be one greater than the specialisation depth of the parent archetype"),
    VARCN("The node_id of the root object of the archetype must be of the form id1{.1}*, where the number of .1 components equals the specalisation depth, and must be defined in the terminology"),
    VARRV("The rm_release top-level meta-data item must exist and consist of a valid 3-part version identifier."),
    VARAV("The adl_version top-level meta-data item must exist and consist of a valid 3-part version identifier."),
    VDIFV("archetype attribute differential path validity: an archetype may only have a differential path if it is specialised."),
    VDIFP("specialised archetype attribute differential path validity: if an attribute constraint has a differential path, the path must exist in the flat parent, and also be valid with respect to the reference model, i.e. in the sense that it corresponds to a legal potential construction of objects."),
    VATCV("the give id code is not valid"),
    VTSD("specialisation level of codes. Term or constraint code defined in archetype terminology must be of the same specialisation level as the archetype (differential archetypes), or the same or a less specialised level (flat archetypes)"),
    VTTBK("terminology term binding key valid. Every term binding must be to either a defined archetype term ('at-code') or to a path that is valid in the flat archetype.");




    private final String description;

    ErrorType(String description){
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        return name() + ": " + description;
    }
}
