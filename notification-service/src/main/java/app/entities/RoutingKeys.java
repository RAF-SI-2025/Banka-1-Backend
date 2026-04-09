package app.entities;

/**
 * Konstante za routing key-eve koje se koriste u RabbitMQ porukama.
 * Ove vrednosti se mapiraju na tipove notifikacija definisane u application.properties.
 */
public final class RoutingKeys {
    /**
     * Routing key za kreiranje zaposlenog.
     */
    public static final String EMPLOYEE_CREATED = "employee.created";
    /**
     * Routing key za reset lozinke zaposlenog.
     */
    public static final String EMPLOYEE_PASSWORD_RESET = "employee.password_reset";
    /**
     * Routing key za deaktivaciju naloga zaposlenog.
     */
    public static final String EMPLOYEE_ACCOUNT_DEACTIVATED = "employee.account_deactivated";
    /**
     * Routing key za kreiranje klijenta.
     */
    public static final String CLIENT_CREATED = "client.created";
    /**
     * Routing key za reset lozinke klijenta.
     */
    public static final String CLIENT_PASSWORD_RESET = "client.password_reset";
    /**
     * Routing key za deaktivaciju naloga klijenta.
     */
    public static final String CLIENT_ACCOUNT_DEACTIVATED = "client.account_deactivated";
    /**
     * Routing key za blokiranje kartice.
     */
    public static final String CARD_REQUEST_VERIFICATION = "card.request_verification";
    /**
     * Routing key za uspesno kreiranje kartice kroz request flow.
     */
    public static final String CARD_REQUEST_SUCCESS = "card.request_success";
    /**
     * Routing key za neuspesno kreiranje kartice kroz request flow.
     */
    public static final String CARD_REQUEST_FAILURE = "card.request_failure";
    /**
     * Routing key za blokiranje kartice.
     */
    public static final String CARD_BLOCKED = "card.blocked";
    /**
     * Routing key za odblokiranje kartice.
     */
    public static final String CARD_UNBLOCKED = "card.unblocked";
    /**
     * Routing key za trajnu deaktivaciju kartice.
     */
    public static final String CARD_DEACTIVATED = "card.deactivated";
    /**
     * Routing key za odobren kreditni zahtev.
     */
    public static final String CREDIT_APPROVED = "credit.approved";
    /**
     * Routing key za odbijen kreditni zahtev.
     */
    public static final String CREDIT_DECLINED = "credit.declined";
    /**
     * Routing key za neuspesnu naplatu rate kredita.
     */
    public static final String CREDIT_INSTALLMENT_FAILED = "credit.installment_failed";
    /**
     * Routing key za odobren order.
     */
    public static final String ORDER_APPROVED = "order.approved";
    /**
     * Routing key za odbijen order.
     */
    public static final String ORDER_DECLINED = "order.declined";
    /**
     * Routing key za uspesno naplaceni porez.
     */
    public static final String TAX_COLLECTED = "tax.collected";

    private RoutingKeys() {}
}
