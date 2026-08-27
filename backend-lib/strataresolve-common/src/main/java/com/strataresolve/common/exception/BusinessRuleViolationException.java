package com.strataresolve.common.exception;

/**
 * Thrown when a domain business rule prevents the requested operation.
 */
public class BusinessRuleViolationException extends BaseBusinessException {

    public BusinessRuleViolationException(String message) {
        super(message, "BUSINESS_RULE_VIOLATION");
    }
}
