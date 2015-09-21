package org.kuali.ole.ncip.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.extensiblecatalog.ncip.v2.service.*;
import org.kuali.ole.OLEConstants;
import org.kuali.ole.bo.OLECheckOutItem;
import org.kuali.ole.ncip.bo.OLENCIPConstants;
import org.kuali.ole.ncip.converter.OLECheckOutItemConverter;
import org.kuali.ole.ncip.service.NCIPCheckOutItemResponseBuilder;
import org.kuali.ole.ncip.service.OLECheckOutItemService;
import org.kuali.ole.ncip.util.OLENCIPUtil;
import org.kuali.ole.utility.OleStopWatch;
import org.kuali.rice.core.api.config.property.ConfigContext;
import org.kuali.rice.core.api.resourceloader.GlobalResourceLoader;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenchulakshmig on 8/17/15.
 */
public class OLENCIPCheckOutItemServiceImpl extends OLENCIPUtil implements OLECheckOutItemService {

    private static final Logger LOG = Logger.getLogger(OLENCIPCheckOutItemServiceImpl.class);

    private OLECirculationHelperServiceImpl oleCirculationHelperService;

    public NCIPCheckOutItemResponseBuilder getNCIPCheckOutItemResponseBuilder() {
        return new NCIPCheckOutItemResponseBuilder();
    }

    public OLECirculationHelperServiceImpl getOleCirculationHelperService() {
        if (null == oleCirculationHelperService) {
            oleCirculationHelperService = GlobalResourceLoader.getService(OLENCIPConstants.CIRCULATION_HELPER_SERVICE);
        }
        return oleCirculationHelperService;
    }

    public void setOleCirculationHelperService(OLECirculationHelperServiceImpl oleCirculationHelperService) {
        this.oleCirculationHelperService = oleCirculationHelperService;
    }

    @Override
    public CheckOutItemResponseData performService(CheckOutItemInitiationData checkOutItemInitiationData, ServiceContext serviceContext, RemoteServiceManager remoteServiceManager) throws ServiceException, ValidationException {
        OleStopWatch oleStopWatch = new OleStopWatch();
        oleStopWatch.start();

        NCIPCheckOutItemResponseBuilder ncipCheckOutItemResponseBuilder = getNCIPCheckOutItemResponseBuilder();
        CheckOutItemResponseData checkOutItemResponseData = new CheckOutItemResponseData();

        AgencyId agencyId = validateAgency(checkOutItemInitiationData.getInitiationHeader(), checkOutItemResponseData);
        if (null == agencyId) return checkOutItemResponseData;

        boolean userValid = validateUser(checkOutItemInitiationData.getUserId(), checkOutItemResponseData);
        if (!userValid) return checkOutItemResponseData;

        String itemBarcode = checkOutItemInitiationData.getItemId().getItemIdentifierValue();
        String patronBarcode = checkOutItemInitiationData.getUserId().getUserIdentifierValue();
        String operatorId = agencyPropertyMap.get(OLENCIPConstants.OPERATOR_ID);

        Map checoutParameters = new HashMap();
        checoutParameters.put("patronBarcode", patronBarcode);
        checoutParameters.put("operatorId", operatorId);
        checoutParameters.put("itemBarcode", itemBarcode);
        checoutParameters.put("responseFormatType", "XML");

        String responseString = new NonSip2CheckoutItemService().checkoutItem(checoutParameters);
        OLECheckOutItem oleCheckOutItem = (OLECheckOutItem) new OLECheckOutItemConverter().generateCheckoutItemObject(responseString);

        if (oleCheckOutItem != null && StringUtils.isNotBlank(oleCheckOutItem.getMessage())) {
            if (oleCheckOutItem.getMessage() != null && oleCheckOutItem.getMessage().equals(ConfigContext.getCurrentContextConfig().getProperty(OLEConstants.SUCCESSFULLEY_LOANED))) {
                if (oleCheckOutItem.getDueDate() != null) {
                    ncipCheckOutItemResponseBuilder.setDateDue(checkOutItemResponseData, getOleCirculationHelperService().getGregorianCalendarDate(oleCheckOutItem.getDueDate().toString()));
                } else {
                    ncipCheckOutItemResponseBuilder.setDateDue(checkOutItemResponseData, getOleCirculationHelperService().getGregorianCalendarDate((new java.sql.Timestamp(new Date(2025, 1, 1).getTime()).toString())));
                }
                ncipCheckOutItemResponseBuilder.setRenewalCount(checkOutItemResponseData, new BigDecimal(oleCheckOutItem.getRenewalCount()));
                ncipCheckOutItemResponseBuilder.setItemId(checkOutItemResponseData, checkOutItemInitiationData, agencyId, agencyPropertyMap.get(OLENCIPConstants.ITEM_TYPE));
                ncipCheckOutItemResponseBuilder.setUserId(checkOutItemResponseData, checkOutItemInitiationData, agencyId, oleCheckOutItem.getUserType());
            } else {
                String problemElement = OLENCIPConstants.ITEM;
                String problemValue = itemBarcode;

                if (oleCheckOutItem.getCode().equals("002")) {
                    problemElement = OLENCIPConstants.USER;
                    problemValue = patronBarcode;
                } else if (oleCheckOutItem.getCode().equals("026")) {
                    problemValue = operatorId;
                }
                processProblems(checkOutItemResponseData, problemValue, oleCheckOutItem.getMessage(), problemElement);
            }
        } else {
            processProblems(checkOutItemResponseData, itemBarcode, ConfigContext.getCurrentContextConfig().getProperty(OLENCIPConstants.CHECK_OUT_FAIL), OLENCIPConstants.ITEM);
        }
        oleStopWatch.end();
        LOG.info("Time taken to perform checkout item service : " + oleStopWatch.getTotalTime());
        return checkOutItemResponseData;
    }

}
