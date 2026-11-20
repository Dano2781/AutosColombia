package co.autoscolombia.web;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import co.autoscolombia.service.AccessService;
import co.autoscolombia.service.AdminService;
import co.autoscolombia.service.PaymentService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WebController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentWebControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean private AccessService accessService;
    @MockBean private AdminService adminService;
    @MockBean private PaymentService paymentService;

    @Test
    void displaysThePaymentListWithServiceResults() throws Exception {
        when(paymentService.payments(isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of(
                Map.of("id_pago", 7L, "placa", "ABC123", "estado", "PAGADO")));

        mockMvc.perform(get("/pagos"))
                .andExpect(status().isOk())
                .andExpect(view().name("pagos"))
                .andExpect(model().attributeExists("pagos"));
    }
}
