package org.kuali.ole.spring.batch.processor;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.kuali.ole.docstore.common.constants.DocstoreConstants;
import org.kuali.ole.constants.OleNGConstants;
import org.kuali.ole.oleng.batch.profile.model.BatchProcessProfile;
import org.kuali.ole.oleng.batch.profile.model.BatchProfileAddOrOverlay;
import org.kuali.ole.oleng.batch.profile.model.BatchProfileDataMapping;
import org.kuali.ole.util.StringUtil;
import org.kuali.ole.utility.OleDsNgRestClient;
import org.kuali.rice.core.api.config.property.ConfigContext;
import org.kuali.rice.krad.util.ObjectUtils;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by SheikS on 12/9/2015.
 */
@Service("batchBibFileProcessor")
public class BatchBibFileProcessor extends BatchFileProcessor {
    private Map<String, String> operationIndMap;
    private Map<String, String> matchOptionIndMap;
    private Map<String, String> dataTypeIndMap;
    private static final Logger LOG = Logger.getLogger(BatchBibFileProcessor.class);

    @Override
    public String processRecords(List<Record> records, BatchProcessProfile batchProcessProfile) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        String response = "";
        for (Iterator<Record> iterator = records.iterator(); iterator.hasNext(); ) {
            JSONObject jsonObject = null;

            Record marcRecord = iterator.next();
            if (!batchProcessProfile.getBatchProfileMatchPointList().isEmpty()) {
                String query = getMatchPointProcessor().prepareSolrQueryMapForMatchPoint(marcRecord, batchProcessProfile.getBatchProfileMatchPointList());

                if (StringUtils.isNotBlank(query)) {
                    List results = getSolrRequestReponseHandler().getSolrDocumentList(query);
                    if (null == results || results.size() > 1) {
                        System.out.println("**** More than one record found for query : " + query);
                        return null;
                    }

                    if (null != results && results.size() == 1) {
                        SolrDocument solrDocument = (SolrDocument) results.get(0);
                        String bibId = (String) solrDocument.getFieldValue(DocstoreConstants.LOCALID_DISPLAY);
                        jsonObject = prepareRequest(bibId, marcRecord, batchProcessProfile);
                    } else {
                        jsonObject = prepareRequest(null, marcRecord, batchProcessProfile);
                    }
                }
            } else {
                jsonObject = prepareRequest(null, marcRecord, batchProcessProfile);
            }
            jsonArray.put(jsonObject);
        }

        if (jsonArray.length() > 0) {
            response = getOleDsNgRestClient().postData(OleDsNgRestClient.Service.PROCESS_BIB_HOLDING_ITEM, jsonArray, OleDsNgRestClient.Format.JSON);
        }
        return response;
    }

    private String getOperationInd(String operation) {
        return getOperationIndMap().get(operation);
    }

    private String getMatchOptionInd(String matchOption) {
        return getMatchOptionIndMap().get(matchOption);
    }

    private String getDataTypeInd(String dataType) {
        return getDataTypeIndMap().get(dataType);
    }

    /**
     * @param bibId
     * @param marcRecord
     * @param batchProcessProfile
     * @return The method accepts a bibId, MarcRecord for the bib and the profile. It sets up the
     * main bibData json object that contains the respective holdings, items json objects.
     * @throws JSONException
     */
    private JSONObject prepareRequest(String bibId, Record marcRecord, BatchProcessProfile batchProcessProfile) throws JSONException {
        LOG.info("Preparing JSON Request for Bib/Holdings/Items");

        JSONObject bibData = new JSONObject();
        String unmodifiedRecord = getMarcXMLConverter().generateMARCXMLContent(marcRecord);
        String updatedUserName = getUpdatedUserName();
        String updatedDate = DocstoreConstants.DOCSTORE_DATE_FORMAT.format(new Date());

        if (null != bibId) {
            bibData.put("id", bibId);
        }

        bibData.put(OleNGConstants.TAG_001, getMarcRecordUtil().getControlFieldValue(marcRecord, OleNGConstants.TAG_001));
        bibData.put(OleNGConstants.UPDATED_BY, updatedUserName);
        bibData.put(OleNGConstants.UPDATED_DATE, updatedDate);
        bibData.put(OleNGConstants.UNMODIFIED_CONTENT, unmodifiedRecord);
        bibData.put(OleNGConstants.OPS, getOverlayOps(batchProcessProfile));

        // Prepare data mapping before MARC Transformation
        Map<String, List<JSONObject>> dataMappingsMapPreTransformation = prepareDataMapping(marcRecord, batchProcessProfile, OleNGConstants.PRE_MARC_TRANSFORMATION);


        //Transformations pertaining to MARC record (001,003,035$a etc..)
        handleBatchProfileTransformations(marcRecord, batchProcessProfile);
        String modifiedRecord = getMarcXMLConverter().generateMARCXMLContent(marcRecord);
        bibData.put(OleNGConstants.MODIFIED_CONTENT, modifiedRecord);

        // Prepare data mapping after MARC Transformation
        Map<String, List<JSONObject>> dataMappingsMapPostTransformations = prepareDataMapping(marcRecord, batchProcessProfile, OleNGConstants.POST_MARC_TRANSFORMATION);


        List<JSONObject> bibDataMappingsPreTrans = dataMappingsMapPreTransformation.get(OleNGConstants.BIB_DATAMAPPINGS);
        List<JSONObject> bibDataMappingsPostTrans = dataMappingsMapPostTransformations.get(OleNGConstants.BIB_DATAMAPPINGS);
        bibData.put(OleNGConstants.DATAMAPPING, buildOneObjectForList(bibDataMappingsPreTrans, bibDataMappingsPostTrans));

        JSONObject holdingsData = getMatchPointProcessor().prepareMatchPointsForHoldings(marcRecord, batchProcessProfile);
        List<JSONObject> holdingsDataMappingsPreTrans = dataMappingsMapPreTransformation.get(OleNGConstants.HOLDINGS_DATAMAPPINGS);
        List<JSONObject> holdingsDataMappingsPostTrans = dataMappingsMapPostTransformations.get(OleNGConstants.HOLDINGS_DATAMAPPINGS);
        holdingsData.put(OleNGConstants.DATAMAPPING, buildOneObjectForList(holdingsDataMappingsPreTrans, holdingsDataMappingsPostTrans));
        bibData.put(OleNGConstants.HOLDINGS, holdingsData);

        JSONObject eholdingsData = getMatchPointProcessor().prepareMatchPointsForEHoldings(marcRecord, batchProcessProfile);
        List<JSONObject> eholdingsDataMappingsPreTrans = dataMappingsMapPreTransformation.get(OleNGConstants.EHOLDINGS_DATAMAPPINGS);
        List<JSONObject> eholdingsDataMappingsPostTrans = dataMappingsMapPostTransformations.get(OleNGConstants.EHOLDINGS_DATAMAPPINGS);
        eholdingsData.put(OleNGConstants.DATAMAPPING, buildOneObjectForList(eholdingsDataMappingsPreTrans, eholdingsDataMappingsPostTrans));
        bibData.put(OleNGConstants.EHOLDINGS, eholdingsData);

        JSONObject itemData = getMatchPointProcessor().prepareMatchPointsForItem(marcRecord, batchProcessProfile);
        List<JSONObject> itemsDataMappingsPreTrans = dataMappingsMapPreTransformation.get(OleNGConstants.ITEM_DATAMAPPINGS);
        List<JSONObject> itemsDataMappingsPostTrans = dataMappingsMapPostTransformations.get(OleNGConstants.ITEM_DATAMAPPINGS);
        itemData.put(OleNGConstants.DATAMAPPING, buildOneObjectForList(itemsDataMappingsPreTrans, itemsDataMappingsPostTrans));
        bibData.put(OleNGConstants.ITEM, itemData);

        return bibData;
    }

    private JSONObject buildOneObject(JSONObject bibDataMappingsPreTrans, JSONObject bibDataMappingsPostTrans) {
        JSONObject finalObject = new JSONObject();

        for (Iterator iterator = bibDataMappingsPreTrans.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            try {
                finalObject.put(key, bibDataMappingsPreTrans.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        for (Iterator iterator = bibDataMappingsPostTrans.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            try {
                finalObject.put(key, bibDataMappingsPostTrans.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return finalObject;
    }

    private List<JSONObject> buildOneObjectForList(List<JSONObject> dataMappingsPreTrans, List<JSONObject> dataMappingsPostTrans) {
        List<JSONObject> finalObjects = new ArrayList<>();

        for(int index = 0; index < dataMappingsPreTrans.size() ; index++) {
            JSONObject preTransformObject = dataMappingsPreTrans.get(index);
            JSONObject postTransformObject = dataMappingsPostTrans.get(index);
            finalObjects.add(buildOneObject(preTransformObject,postTransformObject));
        }

        return finalObjects;
    }

    private Map<String, List<JSONObject>> prepareDataMapping(Record marcRecord, BatchProcessProfile batchProcessProfile, String transformationOption) throws JSONException {
        Map<String, List<JSONObject>> dataMappings = new HashMap<>();

        List<JSONObject> bibDataMappings = prepareDataMappings(Collections.singletonList(marcRecord), batchProcessProfile, OleNGConstants.BIBLIOGRAPHIC, transformationOption);
        dataMappings.put(OleNGConstants.BIB_DATAMAPPINGS, bibDataMappings);

        List<JSONObject> holdingsDataMappings = prepareDataMappings(Collections.singletonList(marcRecord), batchProcessProfile, OleNGConstants.HOLDINGS, transformationOption);
        dataMappings.put(OleNGConstants.HOLDINGS_DATAMAPPINGS, holdingsDataMappings);

        List<JSONObject> itemDataMappings = prepareDataMappings(Collections.singletonList(marcRecord), batchProcessProfile, OleNGConstants.ITEM, transformationOption);
        dataMappings.put(OleNGConstants.ITEM_DATAMAPPINGS, itemDataMappings);

        List<Record> recordListForEHoldings = getRecordListForEHoldings(marcRecord, batchProcessProfile, OleNGConstants.EHOLDINGS);
        List<JSONObject> eholdingsDataMappings = prepareDataMappings(recordListForEHoldings, batchProcessProfile, OleNGConstants.EHOLDINGS, transformationOption);
        dataMappings.put(OleNGConstants.EHOLDINGS_DATAMAPPINGS, eholdingsDataMappings);

        return dataMappings;
    }

    private List<Record> getRecordListForEHoldings(Record marcRecord, BatchProcessProfile batchProcessProfile, String docType) {
        String multiTagField = getMultiTagField(batchProcessProfile,docType);
        List<Record> records = new ArrayList<>();
        if(StringUtils.isNotBlank(multiTagField)) {
            records =  splitRecordByMultiValue(marcRecord, multiTagField);
        }
        return CollectionUtils.isNotEmpty(records) ? records : Collections.singletonList(marcRecord);
    }

    private String getMultiTagField(BatchProcessProfile batchProcessProfile, String docType) {
        List<BatchProfileAddOrOverlay> batchProfileAddOrOverlayList = batchProcessProfile.getBatchProfileAddOrOverlayList();
        if(CollectionUtils.isNotEmpty(batchProfileAddOrOverlayList)) {
            for (Iterator<BatchProfileAddOrOverlay> iterator = batchProfileAddOrOverlayList.iterator(); iterator.hasNext(); ) {
                BatchProfileAddOrOverlay batchProfileAddOrOverlay = iterator.next();
                if(batchProfileAddOrOverlay.getDataType().equalsIgnoreCase(docType)) {
                    if(batchProfileAddOrOverlay.getMatchOption().equalsIgnoreCase(OleNGConstants.IF_NOT_MATCH_FOUND)) {
                        return batchProfileAddOrOverlay.getDataField();
                    }
                }
            }
        }
        return null;
    }

    public List getOverlayOps(BatchProcessProfile batchProcessProfile) {
        List addOverlayOps = new ArrayList();

        List<BatchProfileAddOrOverlay> batchProfileAddOrOverlayList = batchProcessProfile.getBatchProfileAddOrOverlayList();
        for (Iterator<BatchProfileAddOrOverlay> iterator = batchProfileAddOrOverlayList.iterator(); iterator.hasNext(); ) {
            BatchProfileAddOrOverlay batchProfileAddOrOverlay = iterator.next();
            String dataType = batchProfileAddOrOverlay.getDataType();
            String matchOption = batchProfileAddOrOverlay.getMatchOption();
            String operation = batchProfileAddOrOverlay.getOperation();

            String matchOptionInd = getMatchOptionInd(matchOption);
            String dataTypeInd = getDataTypeInd(dataType);
            String operationInd = getOperationInd(operation);
            addOverlayOps.add(matchOptionInd + dataTypeInd + operationInd);
        }
        return addOverlayOps;
    }

    /**
     * @param marcRecords
     * @param batchProcessProfile
     * @param docType
     * @param transformationOption
     * @return The method returns a list of key/value pairs where key is the destination field and value if the value determined from the profile's data maping section.
     * It handles the following usecases:
     * 1. Dest field mapping from multiple tags (For example: CallNumber determined from 050$b and 050$b - Here it will take the values and concatenate with a space in between)
     * 2. Dest field mapping for multi-valued fields (For example: Public Note determined from 856$z and 856$3 - Here it will form a list of values)
     * 3. Handling priority when determining values for the destination field.
     * @throws JSONException
     */
    public List<JSONObject> prepareDataMappings(List<Record> marcRecords, BatchProcessProfile batchProcessProfile, String docType, String transformationOption) throws JSONException {
        List<JSONObject> dataMappings = new ArrayList<>();
        List<BatchProfileDataMapping> filteredDataMappings = filterDataMappingsByTransformationOption(batchProcessProfile.getBatchProfileDataMappingList(), transformationOption);
        sortDataMappings(filteredDataMappings);
        for (Iterator<Record> recordIterator = marcRecords.iterator(); recordIterator.hasNext(); ) {
            Map<String, List<ValueByPriority>> valueByPriorityMap = new HashedMap();
            Record marcRecord = recordIterator.next();
            for (Iterator<BatchProfileDataMapping> iterator = filteredDataMappings.iterator(); iterator.hasNext(); ) {
                BatchProfileDataMapping batchProfileDataMapping = iterator.next();
                String destination = batchProfileDataMapping.getDestination();
                if (destination.equalsIgnoreCase(docType)) {
                    String destinationField = batchProfileDataMapping.getField();


                    boolean multiValue = batchProfileDataMapping.isMultiValue();

                    List<String> fieldValues = getFieldValues(marcRecord, batchProfileDataMapping, multiValue);

                    if (CollectionUtils.isNotEmpty(fieldValues)) {
                        int priority = batchProfileDataMapping.getPriority();

                        buildingValuesForDestinationBasedOnPriority(valueByPriorityMap, destinationField, multiValue, fieldValues, priority);
                    }
                }
            }
            dataMappings.add(buildDataMappingsJSONObject(valueByPriorityMap));
        }

        return dataMappings;
    }

    private JSONObject buildDataMappingsJSONObject(Map<String, List<ValueByPriority>> valueByPriorityMap) throws JSONException {
        JSONObject dataMappings = new JSONObject();

        for (Iterator<String> iterator = valueByPriorityMap.keySet().iterator(); iterator.hasNext(); ) {
            String destField = iterator.next();
            List<ValueByPriority> vals = valueByPriorityMap.get(destField);
            for (Iterator<ValueByPriority> iterator1 = vals.iterator(); iterator1.hasNext(); ) {
                ValueByPriority valueByPriority = iterator1.next();
                List<String> values = valueByPriority.getValues();
                if (CollectionUtils.isNotEmpty(values)) {
                    dataMappings.put(destField, values);
                    break;
                }
            }
        }

        return dataMappings;
    }

    /**
     * @param valueByPriorityMap
     * @param destinationField
     * @param multiValue
     * @param fieldValues
     * @param priority
     * @return The valueByPriorityMap contains valueByPriority pojo for each destination field.
     */
    private Map<String, List<ValueByPriority>> buildingValuesForDestinationBasedOnPriority(Map<String, List<ValueByPriority>> valueByPriorityMap, String destinationField, boolean multiValue, List<String> fieldValues, int priority) {
        List<ValueByPriority> valueByPriorities;

        ValueByPriority valueByPriority = new ValueByPriority();
        valueByPriority.setField(destinationField);
        valueByPriority.setPriority(priority);
        valueByPriority.setMultiValue(multiValue);

        valueByPriority.setValues(fieldValues);

        if (valueByPriorityMap.containsKey(destinationField)) {
            valueByPriorities = valueByPriorityMap.get(destinationField);

            if (valueByPriorities.contains(valueByPriority)) {
                ValueByPriority existingValuePriority = valueByPriorities.get(valueByPriorities.indexOf(valueByPriority));
                List<String> values = existingValuePriority.getValues();
                values.addAll(fieldValues);
                StringBuilder stringBuilder = new StringBuilder();
                if (!multiValue) {
                    for (Iterator<String> iterator = values.iterator(); iterator.hasNext(); ) {
                        String value = iterator.next();
                        stringBuilder.append(value);
                        if(iterator.hasNext()){
                            stringBuilder.append(" ");
                        }
                    }
                    values.clear();
                    values.add(stringBuilder.toString());
                }
            } else {
                valueByPriorities.add(valueByPriority);
            }
        } else {
            valueByPriorities = new ArrayList<>();
            valueByPriorities.add(valueByPriority);
        }
        valueByPriorityMap.put(destinationField, valueByPriorities);

        return valueByPriorityMap;
    }

    private List<String> getFieldValues(Record marcRecord, BatchProfileDataMapping batchProfileDataMapping, boolean multiValue) {
        List<String> marcValues = new ArrayList<>();
        if (batchProfileDataMapping.getDataType().equalsIgnoreCase(OleNGConstants.BIB_MARC)) {
            String marcValue;
            String dataField = batchProfileDataMapping.getDataField();
            if (StringUtils.isNotBlank(dataField)) {
                if (getMarcRecordUtil().isControlField(dataField)) {
                    marcValue = getMarcRecordUtil().getControlFieldValue(marcRecord, dataField);
                    marcValues.add(marcValue);
                } else {
                    String ind1 = (batchProfileDataMapping.getInd1() != null ? String.valueOf(batchProfileDataMapping.getInd1().charAt(0)) : " ");
                    String ind2 = (batchProfileDataMapping.getInd2() != null ? String.valueOf(batchProfileDataMapping.getInd2().charAt(0)) : " ");
                    String subField = batchProfileDataMapping.getSubField();
                    if (multiValue) {
                        marcValues = getMarcRecordUtil().getMultiDataFieldValues(marcRecord, dataField, ind1, ind2, subField);
                    } else {
                        marcValue = getMarcRecordUtil().getDataFieldValueWithIndicators(marcRecord, dataField, ind1, ind2, subField);
                        if (StringUtils.isNotBlank(marcValue)) {
                            marcValues.add(marcValue);
                        }
                    }
                }
            }
        } else {
            String constantValue = batchProfileDataMapping.getConstant();
            if (StringUtils.isNotBlank(constantValue)) {
                marcValues.add(constantValue);
            }
        }
        return marcValues;
    }


    private List<BatchProfileDataMapping> filterDataMappingsByTransformationOption(List<BatchProfileDataMapping> batchProfileDataMappingList, String transformationOption) {
        List<BatchProfileDataMapping> batchProfileDataMappings = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(batchProfileDataMappingList)) {
            for (Iterator<BatchProfileDataMapping> iterator = batchProfileDataMappingList.iterator(); iterator.hasNext(); ) {
                BatchProfileDataMapping batchProfileDataMapping = iterator.next();
                if (batchProfileDataMapping.getTransferOption().equalsIgnoreCase(transformationOption)) {
                    batchProfileDataMappings.add(batchProfileDataMapping);
                }
            }
        }
        return batchProfileDataMappings;
    }

    private void sortDataMappings(List<BatchProfileDataMapping> filteredDataMappings) {
        Collections.sort(filteredDataMappings, new Comparator<BatchProfileDataMapping>() {
            public int compare(BatchProfileDataMapping dataMapping1, BatchProfileDataMapping dataMapping2) {
                int priorityForDataMapping1 = dataMapping1.getPriority();
                int priorityForDataMapping2 = dataMapping2.getPriority();
                return new Integer(priorityForDataMapping1).compareTo(new Integer(priorityForDataMapping2));
            }
        });
    }

    public Map<String, String> getDataMappingMap(List<BatchProfileDataMapping> batchProfileDataMappingList) {
        Map<String, String> dataMappingMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(batchProfileDataMappingList)) {
            for (Iterator<BatchProfileDataMapping> iterator = batchProfileDataMappingList.iterator(); iterator.hasNext(); ) {
                BatchProfileDataMapping batchProfileDataMapping = iterator.next();
                String mapKey = batchProfileDataMapping.getDestination() + "-"
                        + batchProfileDataMapping.getField();
                String value = batchProfileDataMapping.getDataField() + " $" + batchProfileDataMapping.getSubField();
                if (dataMappingMap.containsKey(mapKey)) {
                    value = dataMappingMap.get(mapKey);
                    value = value + "$" + batchProfileDataMapping.getSubField();
                }
                dataMappingMap.put(mapKey, value);
            }
        }
        return dataMappingMap;
    }


    private void handleBatchProfileTransformations(Record record, BatchProcessProfile batchProcessProfile) {
        new StepsProcessor().processSteps(record, batchProcessProfile);
    }

    public Map<String, String> getOperationIndMap() {
        if (null == operationIndMap) {
            operationIndMap = new TreeMap(String.CASE_INSENSITIVE_ORDER);
            operationIndMap.put(OleNGConstants.ADD, OleNGConstants.ONE);
            operationIndMap.put(OleNGConstants.OVERLAY, OleNGConstants.TWO);
            operationIndMap.put(OleNGConstants.DISCARD, OleNGConstants.THREE);
        }
        return operationIndMap;
    }

    public void setOperationIndMap(Map<String, String> operationIndMap) {
        this.operationIndMap = operationIndMap;
    }

    public Map<String, String> getMatchOptionIndMap() {
        if (null == matchOptionIndMap) {
            matchOptionIndMap = new TreeMap(String.CASE_INSENSITIVE_ORDER);
            matchOptionIndMap.put(OleNGConstants.IF_MATCH_FOUND, OleNGConstants.ONE);
            matchOptionIndMap.put(OleNGConstants.IF_NOT_MATCH_FOUND, OleNGConstants.TWO);
        }
        return matchOptionIndMap;
    }

    public void setMatchOptionIndMap(Map<String, String> matchOptionIndMap) {
        this.matchOptionIndMap = matchOptionIndMap;
    }

    public Map<String, String> getDataTypeIndMap() {
        if (null == dataTypeIndMap) {
            dataTypeIndMap = new TreeMap(String.CASE_INSENSITIVE_ORDER);
            dataTypeIndMap.put(OleNGConstants.BIBLIOGRAPHIC, OleNGConstants.ONE);
            dataTypeIndMap.put(OleNGConstants.HOLDINGS, OleNGConstants.TWO);
            dataTypeIndMap.put(OleNGConstants.ITEM, OleNGConstants.THREE);
            dataTypeIndMap.put(OleNGConstants.EHOLDINGS, OleNGConstants.FOUR);
        }
        return dataTypeIndMap;
    }

    public void setDataTypeIndMap(Map<String, String> dataTypeIndMap) {
        this.dataTypeIndMap = dataTypeIndMap;
    }

    @Override
    public String getReportingFilePath() {
        return ConfigContext.getCurrentContextConfig().getProperty("batch.bibImport.directory");
    }


    private class ValueByPriority {
        private int priority;
        private String field;
        private boolean multiValue;
        private List<String> values;

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public boolean isMultiValue() {
            return multiValue;
        }

        public void setMultiValue(boolean multiValue) {
            this.multiValue = multiValue;
        }

        public List<String> getValues() {
            if (null == values) {
                values = new ArrayList<>();
            }
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }

        public void addValues(String value) {
            if (null != value) {
                getValues().add(value);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ValueByPriority that = (ValueByPriority) o;

            if (priority != that.priority) return false;
            return field != null ? field.equals(that.field) : that.field == null;

        }

        @Override
        public int hashCode() {
            int result = priority;
            result = 31 * result + (field != null ? field.hashCode() : 0);
            return result;
        }
    }

    public List<Record> splitRecordByMultiValue(Record record, String field) {
        List<Record> records = new ArrayList<>();
        List<VariableField> dataFields = record.getVariableFields(field);
        for (Iterator<VariableField> variableFieldIterator = dataFields.iterator(); variableFieldIterator.hasNext(); ) {
            DataField dataField = (DataField) variableFieldIterator.next();
            Record clonedRecord = (Record) ObjectUtils.deepCopy(record);
            getMarcRecordUtil().removeFieldFromRecord(clonedRecord,field);
            getMarcRecordUtil().addVariableFieldToRecord(clonedRecord,dataField);
            records.add(clonedRecord);
        }

        return CollectionUtils.isNotEmpty(records) ? records : Collections.singletonList(record);

    }

}
