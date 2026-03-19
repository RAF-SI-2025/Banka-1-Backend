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

    private RoutingKeys() {}
}
