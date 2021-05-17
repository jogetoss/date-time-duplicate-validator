package org.joget.marketplace;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.DefaultValidator;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.StringUtil;

public class DateTimeDuplicateValidator extends FormValidator {
    // Variables

    private Map<String, Object> properties;
    private Element element;
    private final static String MESSAGE_PATH = "messages/DateTimeDuplicateValidator";
    private String cachedTableName = null;


    public String getName() {
        return "Date Time Duplicate Validator";
    }

    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.DateTimeDuplicateValidator.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    public String getVersion() {
        return "7.0.1";
    }

    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.DateTimeDuplicateValidator.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DateTimeDuplicateValidator.json", null, true, MESSAGE_PATH);
    }

    public String getElementDecoration() {
        String decoration = "";
        String mandatory = (String) getProperty("mandatory");
        if ("true".equals(mandatory)) {
            decoration += " * ";
        }
        if (decoration.trim().length() > 0) {
            decoration = decoration.trim();
        }
        return decoration;
    }

    public boolean validate(Element element, FormData formData, String[] values) {

        Form form = FormUtil.findRootForm(element);
        String id = FormUtil.getElementParameterName(element);
        String mandatory = (String) getProperty("mandatory");
        String extraConditions = getPropertyString("extraCondition");
        String query = "";
        String filterQuery = "";
        String filterValue = "";
        boolean result = true;
        Object[] paramsArray;
        String condition = "";

        String formDefId = form.getPropertyString("id");
        String tableName = getTableName(formDefId);
        
        String resourceFieldId = getPropertyString("resource");
        String startDateFieldId = getPropertyString("startDate");
        String endDateFieldId = getPropertyString("endDate");
        
        String dataFormat = getPropertyString("dataFormat");
        String displayFormat = getPropertyString("displayFormat");
        
        //check for mandatory field
        if ("true".equals(mandatory)){
            if (!validateMandatory(values)){
                formData.addFormError(id, getPropertyString("mandatoryErrorMessage"));
                return false;
            }
        }
        
        //get field value from form data object
        Element resource = FormUtil.findElement(resourceFieldId, form, formData);
        Element startDate = FormUtil.findElement(startDateFieldId, form, formData);
        Element endDate = FormUtil.findElement(endDateFieldId, form, formData);

        //get value of field 
        String recordIdV = formData.getPrimaryKeyValue();
        String resourceV = "";
        if (resource != null){
            resourceV = FormUtil.getElementPropertyValue(resource, formData);
        }
        String startDateV = FormUtil.getElementPropertyValue(startDate, formData);
        String endDateV = FormUtil.getElementPropertyValue(endDate, formData);
                
        //convert date value from display format into data format
        SimpleDateFormat data = new SimpleDateFormat(dataFormat);
        SimpleDateFormat display = new SimpleDateFormat(displayFormat);
        try {
            Date date = display.parse(startDateV);
            startDateV = data.format(date);
        }catch(Exception ex){
            LogUtil.error(DateTimeDuplicateValidator.class.getName(), ex, "Parsing start date error");
            formData.addFormError(id, "Parsing start date error");
            return false;
        }
        
        try {
            Date date = display.parse(endDateV);
            endDateV = data.format(date);
        }catch(Exception ex){
            LogUtil.error(DateTimeDuplicateValidator.class.getName(), ex, "Parsing end date error");
            formData.addFormError(id, "Parsing end date error");
            return false;
        }
        
        if(recordIdV != null && !recordIdV.isEmpty()){
            //existing record, exclude itself from checking
            condition = " WHERE id != ? ";
        }else{
            //new record, no need to exclude itself from checking
            condition = " WHERE '1' = ? ";
            recordIdV = "1";
        }
        
        if (extraConditions != null && !extraConditions.isEmpty()) {
            condition += extraConditions;
        }
        
        Object[] filters = (Object[]) getProperty("filters");
        if (filters != null && filters.length > 0) {
            //filters (grid)
            List<String> valueList = Arrays.asList("id", "dateCreated", "dateModified" , "createdBy", "createdByName", "modifiedBy", "modifiedByName");
            values = null;

            for (Object o : filters) {
                Map filterMap = (HashMap) o;
                DataListFilterQueryObject obj = new DataListFilterQueryObject();
                obj.setOperator(filterMap.get("join").toString());

                String field = (filterMap.get("field").toString());
                if (valueList.contains(field)){
                    field = field;
                } else {
                    field = ("c_" + filterMap.get("field").toString());
                }
                String operator = filterMap.get("operator").toString();
                String value = filterMap.get("value").toString();

                if ("IS TRUE".equals(operator) || "IS FALSE".equals(operator) || "IS NULL".equals(operator) || "IS NOT NULL".equals(operator)) {
                    query = field + " " + operator;
                    values = new String[0];
                } else if ("IN".equals(operator) || "NOT IN".equals(operator)) {
                    List<String> queries = new ArrayList<String>();
                    List<String> valuesList = new ArrayList<String>();
                    values = value.split(";");
                    if (values.length > 0) {
                        for (String v : values) {
                            queries.add("(" + field + " = ? or " + field + " like ? or " + field + " like ? or " + field + " like ?)");
                            valuesList.add(v);
                            valuesList.add(v + ";%");
                            valuesList.add("%;" + v + ";%");
                            valuesList.add("%;" + v);
                        }
                        if ("NOT IN".equals(operator)) {
                            query += "NOT ";
                        }
                        query += "(" + StringUtils.join(queries, " or ") + ")";
                        values = valuesList.toArray(new String[0]);
                    }
                } else if ("=".equals(operator) || "<>".equals(operator)) {
                    query = field + " " + operator + " ?";
                    values = new String[]{value};
                } else if (!value.isEmpty()) {
                    query = field + " " + operator + " ?";
                    values = new String[]{value};
                }

            }
            filterValue = Arrays.toString(values).replaceAll(",|\\]|\\[", "");
            filterQuery = "(" + query + ")";
        }
        
        if (filters != null && filters.length > 0 && !resourceFieldId.isEmpty()) {
            //filters and resource
            condition += " AND " + filterQuery + " AND (c_" + resourceFieldId + " = ?) ";
            paramsArray = new Object[]{recordIdV, filterValue, resourceV, endDateV, startDateV};
        }else if(filters != null && filters.length > 0){
            //filters
            condition += " AND " + filterQuery;
            paramsArray = new Object[]{recordIdV, filterValue, endDateV, startDateV};
        }else if(!resourceFieldId.isEmpty()){
            //resource
            condition += " AND (c_" + resourceFieldId + " = ?) ";
            paramsArray = new Object[]{recordIdV, resourceV, endDateV, startDateV};
        }else{
            //no filter and no resource
            condition += " ";
            paramsArray = new Object[]{recordIdV, endDateV, startDateV};
        }
        //condition += " AND ((c_" + startDateFieldId + " <= ? AND c_" + endDateFieldId + " >= ?) OR (c_" + startDateFieldId + " <= ? AND c_" + endDateFieldId + " >= ?) OR (c_" + startDateFieldId + " >= ? AND c_" + endDateFieldId + " <= ?))";
        //the condition above is not good for booking 15:00 to 16:00 where there are existing times 14:00 to 15:00 and 16:00 to 17:00. by right, should be OK to book/proceed therefore removing the equal sign will solve the issue.
        
        //condition += " AND ((c_" + startDateFieldId + " < ? AND c_" + endDateFieldId + " > ?) OR (c_" + startDateFieldId + " < ? AND c_" + endDateFieldId + " > ?) OR (c_" + startDateFieldId + " > ? AND c_" + endDateFieldId + " < ?))";
        condition += " AND (c_" + startDateFieldId + " < ? AND ? < c_" + endDateFieldId + ")";
        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        Long rowCount = formDataDao.count(formDefId, tableName, condition, paramsArray);
        if (rowCount > 0) {
            String errorMessage = getPropertyString("errorMessage");
            if (errorMessage.contains("#duplicate.count#")) {
                errorMessage = errorMessage.replaceAll(StringUtil.escapeRegex("#duplicate.count#"), rowCount.toString());
            }
            formData.addFormError(id, errorMessage);
            result = false;
        }

        return result;
    }

    protected String getTableName(String formDefId) {
        String tableName = cachedTableName;
        if (tableName == null) {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef != null && formDefId != null) {
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                tableName = appService.getFormTableName(appDef, formDefId);
                cachedTableName = tableName;
            }
        }
        return tableName;
    }
    
    protected boolean validateMandatory(String[] values) {
        boolean result = true;
        
        if (values == null || values.length == 0) {
            result = false;
        } else {
            for (String val : values) {
                if (val == null || val.trim().length() == 0) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }
}
