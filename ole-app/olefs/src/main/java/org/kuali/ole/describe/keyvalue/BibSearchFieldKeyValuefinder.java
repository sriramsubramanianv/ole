package org.kuali.ole.describe.keyvalue;

import org.apache.commons.lang.StringUtils;
import org.kuali.ole.describe.form.BoundwithForm;
import org.kuali.ole.describe.form.GlobalEditForm;
import org.kuali.ole.describe.form.OLESearchForm;
import org.kuali.ole.docstore.common.document.config.DocFieldConfig;
import org.kuali.ole.docstore.common.document.config.DocFormatConfig;
import org.kuali.ole.docstore.common.document.config.DocTypeConfig;
import org.kuali.ole.docstore.common.document.config.DocumentSearchConfig;
import org.kuali.ole.docstore.model.enums.DocType;
import org.kuali.rice.core.api.util.ConcreteKeyValue;
import org.kuali.rice.core.api.util.KeyValue;
import org.kuali.rice.krad.uif.control.UifKeyValuesFinderBase;
import org.kuali.rice.krad.uif.field.InputField;
import org.kuali.rice.krad.uif.view.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by jayabharathreddy on 2/16/15.
 */
public class BibSearchFieldKeyValuefinder extends UifKeyValuesFinderBase {

    DocumentSearchConfig documentSearchConfig = DocumentSearchConfig.getDocumentSearchConfig();

    @Override
    public List<KeyValue> getKeyValues(ViewModel viewModel) {
        setAddBlankOption(false);
        OLESearchForm oleSearchForm = null;
        GlobalEditForm globalEditForm = null;
        BoundwithForm boundwithForm = null;
        String docType = DocType.BIB.getCode();

        if (viewModel instanceof OLESearchForm) {
            oleSearchForm = (OLESearchForm)  viewModel;
        }
        else if (viewModel instanceof GlobalEditForm) {
            globalEditForm = (GlobalEditForm) viewModel;
        }
        else if (viewModel instanceof BoundwithForm) {
            boundwithForm = (BoundwithForm) viewModel;
        }

        List<KeyValue> options = new ArrayList<>();
        Map<String, String> sortedMap = new TreeMap<>();
        for (DocTypeConfig docTypeConfig : documentSearchConfig.getDocTypeConfigs()) {
            if (docTypeConfig.getName().equals(docType)) {
                for (DocFormatConfig docFormatConfig : docTypeConfig.getDocFormatConfigList()) {
                    if (docFormatConfig.getName().equals("marc") && DocType.BIB.getCode().equals(docType)){
                        for (DocFieldConfig docFieldConfig : docFormatConfig.getDocFieldConfigList()) {
                            if((oleSearchForm != null && docType.equalsIgnoreCase(docFieldConfig.getDocType().getName())) ||
                                    (globalEditForm != null && globalEditForm.getDocType().equalsIgnoreCase(docFieldConfig.getDocType().getName())) ||
                                    (boundwithForm != null && boundwithForm.getDocType().equalsIgnoreCase(docFieldConfig.getDocType().getName()))){
                                if(docFieldConfig.isSearchable()){
                                    if (docFieldConfig.getName().endsWith("_search")) {
                                        sortedMap.put(docFieldConfig.getLabel(), docFieldConfig.getName());
                                    } else if (docFieldConfig.getName().equalsIgnoreCase("mdf_035a")) {
                                        sortedMap.put(docFieldConfig.getLabel(), docFieldConfig.getName());
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }

        for (String searchField : sortedMap.keySet()) {
            options.add(new ConcreteKeyValue(sortedMap.get(searchField), searchField));
        }
        options.add(0,new ConcreteKeyValue("any","ANY"));
        return options;
    }


}