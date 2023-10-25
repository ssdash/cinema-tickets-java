package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    // Get the logger instance for verbose logging and debug information
    private static final Logger LOGGER = Logger.getLogger(TicketServiceImpl.class.getName());

    // Create instance of external TicketPaymentService
    private final TicketPaymentService ticketPaymentService;

    // Create instance of external SeatReservationService
    private final SeatReservationService seatReservationService;

    /**
     * We can define these constants in a separate class or interface.
     * However, Restricting to implement in TicketServiceImpl class as per instructions.
     */

    // Infant ticket price in GBP
    private final int INFANT_TICKET_PRICE = 0;

    // Child ticket price in GBP
    private final int CHILD_TICKET_PRICE = 10;

    // Adult ticket price in GBP
    private final int ADULT_TICKET_PRICE = 20;

    // Maximum ticket limits can purchase at a time
    private final int MAX_TICKETS_ALLOWED = 20;

    public TicketServiceImpl(TicketPaymentService ticketPaymentService, SeatReservationService seatReservationService) {
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        LOGGER.log(Level.INFO, "Received ticket purchase request for accountId: " + accountId);
        try {
            validateAccountId(accountId);
            validateTicketTypeRequest(ticketTypeRequests);

            // Count total tickets based on a given request
            int totalTickets = countTotalTickets(ticketTypeRequests);
            String message = "Request for reservation " + totalTickets + " tickets.";
            LOGGER.log(Level.INFO, message );

            // Calculate total price based on the total number of tickets
            int totalPrice = calculateTotalTicketPrice(ticketTypeRequests);
            message = "Total payable amount" + totalPrice + " GBP for " + totalTickets + " tickets.";
            LOGGER.log(Level.INFO, message);

            // Make payment through external payment service
            ticketPaymentService.makePayment(accountId, totalPrice);
            LOGGER.log(Level.INFO, "Payment received." );

            // Book tickets through external reservation service
            seatReservationService.reserveSeat(accountId, totalTickets);
            LOGGER.log(Level.INFO, "Reservation confirmed." );

            message = "Total paid: " + totalPrice + " GBP, Total tickets: " + totalTickets;
            LOGGER.log(Level.INFO, message );

            LOGGER.log(Level.INFO, "Tickets purchased successfully!" );

        } catch (InvalidPurchaseException invalidPurchaseException) {
            LOGGER.log(Level.WARNING, "Invalid ticket request: " + invalidPurchaseException.getMessage());
            throw invalidPurchaseException;
        }
    }
    // Calculate total number of tickets based on ticket request
    private int countTotalTickets(TicketTypeRequest[] ticketTypeRequests) {
        int totalTicketsCount = Arrays.stream(ticketTypeRequests)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();

        // Maximum of 20 tickets can be purchased at time
        if (totalTicketsCount > MAX_TICKETS_ALLOWED) {
            String message = " Can't purchase more then " + MAX_TICKETS_ALLOWED + " tickets at a time";
            LOGGER.log(Level.WARNING, message);
            throw new InvalidPurchaseException(message);
        }
        return totalTicketsCount;
    }

    // Ticket price applied based on type of ticket and Infants are not charged at this point
    private int calculateTotalTicketPrice(TicketTypeRequest[] ticketTypeRequests) {
        return Arrays.stream(ticketTypeRequests)
                .mapToInt(ticket -> switch (ticket.getTicketType()) {
                    case INFANT -> INFANT_TICKET_PRICE;
                    case CHILD -> ticket.getNoOfTickets() * CHILD_TICKET_PRICE;
                    case ADULT -> ticket.getNoOfTickets() * ADULT_TICKET_PRICE;
                }).sum();
    }
    // Validate reservation request and cancel if any invalid request.
    private void validateTicketTypeRequest(TicketTypeRequest[] ticketTypeRequests) {
        LOGGER.log(Level.FINE, "Validating ticketType requests.");
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0 || ticketTypeRequests.length > MAX_TICKETS_ALLOWED) {
            LOGGER.log(Level.WARNING, "Invalid ticket requests: " + Arrays.toString(ticketTypeRequests));
            throw new InvalidPurchaseException(("Invalid ticket requests"));
        }

        // Count infant tickets
        long infantRequestCount = Arrays.stream(ticketTypeRequests)
                .filter(ticket -> ticket.getTicketType() == TicketTypeRequest.Type.INFANT)
                .count();

        // Count child tickets
        long childRequestCount = Arrays.stream(ticketTypeRequests)
                .filter(ticket -> ticket.getTicketType() == TicketTypeRequest.Type.CHILD)
                .count();

        // Count adult tickets
        long adultRequestCount = Arrays.stream(ticketTypeRequests)
                .filter(ticket -> ticket.getTicketType() == TicketTypeRequest.Type.ADULT)
                .count();

        // Infant and Child tickets can't be allowed to purchase without Adult ticket
        if ((infantRequestCount > 0 || childRequestCount > 0) && adultRequestCount == 0) {
            String message = "Infant or Child ticket can not be purchased without Adult ticket";
            LOGGER.log(Level.WARNING, message);
            throw new InvalidPurchaseException(message);
        }
    }
    // Account validation
    private void validateAccountId(Long accountId) throws InvalidPurchaseException {
        LOGGER.log(Level.FINE, "Validating accountId: " + accountId);
        if (accountId == null || accountId <= 0) {
            LOGGER.log(Level.WARNING, "Invalid accountId: " + accountId);
            throw new InvalidPurchaseException("Invalid accountId");
        }
    }
}
