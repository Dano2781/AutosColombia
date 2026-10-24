package co.autoscolombia.web;

import co.autoscolombia.service.AccessService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {
    private final AccessService service;
    public WebController(AccessService service) { this.service = service; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/")
    public String index(Model model) { model.addAttribute("username", "Sesión autenticada"); return "index"; }

    @PostMapping("/verificar")
    public String verify(@RequestParam String placa, Model model) {
        model.addAttribute("resultado", service.checkVehicle(placa));
        model.addAttribute("placa", service.normalizePlate(placa));
        return "index";
    }

    @PostMapping("/entrada")
    public String entry(@RequestParam String placa, @RequestParam long idCelda, Authentication auth, Model model) {
        try {
            long id = service.registerEntry(placa, idCelda, auth.getName());
            model.addAttribute("mensaje", "Entrada registrada. Movimiento #" + id);
        } catch (RuntimeException e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("placa", service.normalizePlate(placa));
        return "index";
    }

    @PostMapping("/salida")
    public String exit(@RequestParam String placa, Authentication auth, Model model) {
        try { service.registerExit(placa, auth.getName()); model.addAttribute("mensaje", "Salida registrada y celda liberada."); }
        catch (RuntimeException e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("placa", service.normalizePlate(placa));
        return "index";
    }

    @GetMapping("/movimientos")
    public String movements(@RequestParam(required=false) String placa,
                            @RequestParam(required=false) String estado,
                            @RequestParam(required=false) String desde,
                            @RequestParam(required=false) String hasta, Model model) {
        model.addAttribute("movimientos", service.movements(placa, estado,
                desde == null || desde.isBlank() ? null : LocalDate.parse(desde),
                hasta == null || hasta.isBlank() ? null : LocalDate.parse(hasta)));
        model.addAttribute("placa", placa); model.addAttribute("estado", estado);
        model.addAttribute("desde", desde); model.addAttribute("hasta", hasta);
        return "movimientos";
    }
}
