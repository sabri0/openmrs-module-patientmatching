package org.regenstrief.linkage.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttributeType;
import org.openmrs.module.patientmatching.HibernateConnection;
import org.openmrs.module.patientmatching.LinkDBConnections;
import org.regenstrief.linkage.Record;

public class OpenMRSReader implements DataSourceReader {

    private final int PAGING_SIZE = 10000;

    protected final Log log = LogFactory.getLog(this.getClass());
    
    @SuppressWarnings("unchecked")
    private List patients;

    private Criteria criteria;
    
    private int pageNumber;
    
    private boolean isPatient;

    /**
     * 
     */
    @SuppressWarnings("unchecked")
    public OpenMRSReader() {
        patients = new ArrayList();
        pageNumber = 0;
        isPatient = true;
        
    	log.info("Getting all patient records ...");
    	updatePatientList();
    	
    	log.info("Finish intialization ...");
    }
    
    private Criteria createCriteria(){
        createHibernateSession().clear();
    	criteria = createHibernateSession().createCriteria(Patient.class);

    	criteria.setMaxResults(PAGING_SIZE);
    	criteria.setFirstResult(pageNumber * PAGING_SIZE);
    	return criteria;
    }
    
    private Session createHibernateSession() {
        HibernateConnection connection = new HibernateConnection();
        log.info("Hibernate connection null? " + (connection == null));
        
        SessionFactory sessionFactory = connection.getSessionFactory();
        log.info("Session factory null? " + (sessionFactory == null));
        
        return sessionFactory.getCurrentSession();
    }
    
    @SuppressWarnings("unchecked")
    private void updatePatientList() {
        try {
            isPatient = true;
            patients = createCriteria().list();
        } catch (Exception e) {
            log.info("Iterating one by one on patient records ...");
            createHibernateSession().clear();
            
            String sql = getPatientQuery();
            
            List<PatientIdentifierType> idTypes = LinkDBConnections.getInstance().getPatientIdentifierTypes();
            List<PersonAttributeType> attTypes = LinkDBConnections.getInstance().getPersonAttributeTypes();
            
            String sqlPatientId = "select patient.patientId from Patient as patient order by patient.patientId asc";
            Query queryPatientId = createHibernateSession().createQuery(sqlPatientId)
                                .setMaxResults(PAGING_SIZE).setFirstResult(PAGING_SIZE * pageNumber);
            Iterator patientIds = queryPatientId.iterate();
            
            Integer prevPatientId = null;
            Integer currPatientId = null;
            
            while (patientIds.hasNext()) {
                try {
                    currPatientId = (Integer) patientIds.next();
                    
                    Query query = createHibernateSession().createQuery(sql)
                                            .setParameter("patientId", currPatientId, Hibernate.INTEGER);
                    
                    Iterator patientIter = query.iterate();
                    
                    List<Object> objList = new ArrayList<Object>();
                    
                    if (!patientIter.hasNext()) {
                        // if we can't get any patient data, then just continue to next patient
                        continue;
                    }
                    
                    while (patientIter.hasNext()) {
                      Object[] objects = (Object[]) patientIter.next();
                      // take only the first patient record and then skip the next one
                      // this is done to get only record with preferred name and address
                      // see the query: sort by preferred field on name and address
                      if (currPatientId.equals(prevPatientId)) {
                          continue;
                      } else {
                          objList.addAll(Arrays.asList(objects));
                      }
                      prevPatientId = currPatientId;
                      currPatientId = (Integer) objects[0];
                    }
                    
                    String sqlIdentitifier = "select patient.patientId, id.identifier, idType.name from Patient as patient join patient.identifiers as id " +
                            "join id.identifierType as idType where patient.patientId = :patientId order by patient.patientId asc, idType.name asc";
                    Query queryIdentifier = createHibernateSession().createQuery(sqlIdentitifier)
                            .setParameter("patientId", currPatientId, Hibernate.INTEGER);
                    Iterator iterIdentifier = queryIdentifier.iterate();
                    
                    Map<String, String> mapId = new HashMap<String, String>();
                    while (iterIdentifier.hasNext()) {
                        Object[] oId = (Object[]) iterIdentifier.next();
                        mapId.put(String.valueOf(oId[2]), String.valueOf(oId[1]));
                    }
                    for (PatientIdentifierType idType : idTypes) {
                        String value = mapId.get(idType.getName());
                        if (value != null) {
                            objList.add(value);
                        } else {
                            objList.add("");
                        }
                    }
                    
                    String sqlAttribute = "select patient.patientId, attr.value, attrType.name from Patient as patient join patient.attributes as attr " +
                            "join attr.attributeType as attrType where patient.patientId = :patientId order by patient.patientId asc, attrType.name asc";
                    Query queryAttribute = createHibernateSession().createQuery(sqlAttribute)
                            .setParameter("patientId", currPatientId, Hibernate.INTEGER);
                    Iterator iterAttribute = queryAttribute.iterate();
                    
                    Map<String, String> mapAtt = new HashMap<String, String>();
                    while (iterAttribute.hasNext()) {
                        Object[] oAtt = (Object[]) iterAttribute.next();
                        mapAtt.put(String.valueOf(oAtt[2]), String.valueOf(oAtt[1]));
                    }
                    for (PersonAttributeType attType : attTypes) {
                        String value = mapAtt.get(attType.getName());
                        if (value != null) {
                            objList.add(value);
                        } else {
                            objList.add("");
                        }
                    }
                    patients.add(objList.toArray());
                } catch (HibernateException hex) {
                    log.info("Exception caught during iterating patient ... Skipping ...");
                }
            }
        }
    }

    private String getPatientQuery() {
        StringBuffer selectClause = new StringBuffer();
        for (String patientProperty: LinkDBConnections.getInstance().getPatientPropertyList()) {
            String classProperty = patientProperty.substring(patientProperty.lastIndexOf(".") + 1);
            if (!classProperty.equals("patientId")) {
                selectClause.append("patient.").append(classProperty).append(",");
            }
        }
        
        for (String nameProperty: LinkDBConnections.getInstance().getNamePropertyList()) {
            String classProperty = nameProperty.substring(nameProperty.lastIndexOf(".") + 1);
            selectClause.append("name.").append(classProperty).append(",");
        }
        
        for (String addressProperty: LinkDBConnections.getInstance().getAddressPropertyList()) {
            String classProperty = addressProperty.substring(addressProperty.lastIndexOf(".") + 1);
            selectClause.append("address.").append(classProperty).append(",");
        }
        
        String select = selectClause.substring(0, selectClause.toString().length() - 1);
        
        isPatient = false;
        String sql = "select patient.patientId, " + select +
                            " from Patient as patient join patient.names as name join patient.addresses as address " +
                            " where patient.patientId = :patientId" +
                            " order by patient.patientId asc, name.preferred asc, address.preferred asc";
        return sql;
    }

    /**
     * 
     * @see org.regenstrief.linkage.io.DataSourceReader#close()
     */
    public boolean close() {
        createHibernateSession().clear();
        createHibernateSession().close();
    	patients = null;
    	return (patients == null);
    }

    /**
     * 
     * @see org.regenstrief.linkage.io.DataSourceReader#getRecordSize()
     */
    public int getRecordSize() {
        return -999;
    }

    /**
     * 
     * @see org.regenstrief.linkage.io.DataSourceReader#hasNextRecord()
     */
    public boolean hasNextRecord() {
    	if(patients.size() == 0) {
    		pageNumber ++;
    		updatePatientList();
    	}

    	return (patients.size() > 0);
    }

    /**
     * 
     * @see org.regenstrief.linkage.io.DataSourceReader#nextRecord()
     */
    public Record nextRecord() {
        Record r = null;
        if(patients != null && hasNextRecord()) {
            if (isPatient) {
                Patient p = (Patient) patients.remove(0);
                r = LinkDBConnections.getInstance().patientToRecord(p);
            } else {
                Object[] objs = (Object[]) patients.remove(0);
                r = LinkDBConnections.getInstance().objectsToRecord(objs);
            }
        }
        return r;
    }

    /**
     * 
     * @see org.regenstrief.linkage.io.DataSourceReader#reset()
     */
    @SuppressWarnings("unchecked")
    public boolean reset() {
    	pageNumber = 0;
    	
    	updatePatientList();
        
        return (patients != null);
    }

}