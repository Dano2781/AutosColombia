package co.autoscolombia.web;

import co.autoscolombia.service.AccessService;
import co.autoscolombia.service.AdminService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {
    private final AccessService accessService;
    private final AdminService adminService;

    public WebController(AccessService accessService, AdminService adminService) {
        this.accessService = accessService;
        this.adminService = adminService;
    }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/")
    public String index(Authentication auth, Model model) { model.addAttribute("supervisor", isSupervisor(auth)); return "index"; }

    @PostMapping("/verificar")
    public String verify(@RequestParam String placa, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("resultado", accessService.checkVehicle(placa));
        model.addAttribute("placa", accessService.normalizePlate(placa));
        return "index";
    }

    @PostMapping("/entrada")
    public String entry(@RequestParam String placa, @RequestParam long idCelda, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        try {
            long id = accessService.registerEntry(placa, idCelda, auth.getName());
            model.addAttribute("mensaje", "Entrada registrada. Movimiento #" + id);
        } catch (RuntimeException e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("placa", accessService.normalizePlate(placa));
        return "index";
    }

    @PostMapping("/salida")
    public String exit(@RequestParam String placa, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        try { accessService.registerExit(placa, auth.getName()); model.addAttribute("mensaje", "Salida registrada y celda liberada."); }
        catch (RuntimeException e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("placa", accessService.normalizePlate(placa));
        return "index";
    }

    @GetMapping("/movimientos")
    public String movements(@RequestParam(required=false) String placa,
                            @RequestParam(required=false) String estado,
                            @RequestParam(required=false) String desde,
                            @RequestParam(required=false) String hasta, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("movimientos", accessService.movements(placa, estado,
                desde == null || desde.isBlank() ? null : LocalDate.parse(desde),
                hasta == null || hasta.isBlank() ? null : LocalDate.parse(hasta)));
        model.addAttribute("placa", placa); model.addAttribute("estado", estado);
        model.addAttribute("desde", desde); model.addAttribute("hasta", hasta);
        return "movimientos";
    }

    // Gestión de usuarios
    @GetMapping("/usuarios")
    public String users(@RequestParam(required=false) String buscar,
                        @RequestParam(required=false) String estado, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("usuarios", adminService.users(buscar, estado));
        model.addAttribute("buscar", buscar); model.addAttribute("estado", estado);
        return "usuarios";
    }

    @GetMapping("/usuarios/nuevo")
    public String newUser(Authentication auth, Model model) { model.addAttribute("supervisor", isSupervisor(auth)); return "usuario-form"; }

    @PostMapping("/usuarios/guardar")
    public String saveUser(@RequestParam String tipoDocumento, @RequestParam String numeroDocumento,
                           @RequestParam String nombreCompleto, @RequestParam(required=false) String telefono,
                           @RequestParam(required=false) String email, Authentication auth, Model model) {
        try {
            long id = adminService.createUser(tipoDocumento, numeroDocumento, nombreCompleto, telefono, email, auth.getName());
            return "redirect:/usuarios/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("tipoDocumento", tipoDocumento); model.addAttribute("numeroDocumento", numeroDocumento);
            model.addAttribute("nombreCompleto", nombreCompleto); model.addAttribute("telefono", telefono); model.addAttribute("email", email);
            return "usuario-form";
        }
    }

    @GetMapping("/usuarios/{id}")
    public String userDetail(@PathVariable long id, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("usuario", adminService.user(id));
        model.addAttribute("vehiculos", adminService.vehiclesForUser(id));
        return "usuario-detalle";
    }

    @GetMapping("/usuarios/{id}/editar")
    public String editUser(@PathVariable long id, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("usuario", adminService.user(id));
        return "usuario-form";
    }

    @PostMapping("/usuarios/{id}/editar")
    public String updateUser(@PathVariable long id, @RequestParam String tipoDocumento,
                             @RequestParam String numeroDocumento, @RequestParam String nombreCompleto,
                             @RequestParam(required=false) String telefono, @RequestParam(required=false) String email,
                             Authentication auth, Model model) {
        try {
            adminService.updateUser(id, tipoDocumento, numeroDocumento, nombreCompleto, telefono, email, auth.getName());
            return "redirect:/usuarios/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("usuario", Map.of("id_usuario", id, "tipo_documento", tipoDocumento,
                    "numero_documento", numeroDocumento, "nombre_completo", nombreCompleto,
                    "telefono", telefono == null ? "" : telefono, "email", email == null ? "" : email));
            return "usuario-form";
        }
    }

    @PostMapping("/usuarios/{id}/estado")
    public String userState(@PathVariable long id, @RequestParam String nuevoEstado,
                            Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        try { adminService.changeUserState(id, nuevoEstado, auth.getName()); return "redirect:/usuarios/" + id; }
        catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("usuario", adminService.user(id));
            model.addAttribute("vehiculos", adminService.vehiclesForUser(id));
            return "usuario-detalle";
        }
    }

    // Gestión de celdas
    @GetMapping("/celdas")
    public String cells(@RequestParam(required=false) String buscar,
                        @RequestParam(required=false) String tipo,
                        @RequestParam(required=false) String estado, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("celdas", adminService.cells(buscar, tipo, estado));
        model.addAttribute("buscar", buscar); model.addAttribute("tipo", tipo); model.addAttribute("estado", estado);
        return "celdas";
    }

    @GetMapping("/celdas/nueva")
    public String newCell(Authentication auth, Model model) { model.addAttribute("supervisor", isSupervisor(auth)); return "celda-form"; }

    @PostMapping("/celdas/guardar")
    public String saveCell(@RequestParam String codigo, @RequestParam String tipo,
                           Authentication auth, Model model) {
        try {
            long id = adminService.createCell(codigo, tipo, auth.getName());
            return "redirect:/celdas/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage()); model.addAttribute("codigo", codigo); model.addAttribute("tipo", tipo);
            return "celda-form";
        }
    }

    @GetMapping("/celdas/{id}")
    public String cellDetail(@PathVariable long id, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("celda", adminService.cell(id));
        model.addAttribute("historial", adminService.cellHistory(id));
        return "celda-detalle";
    }

    @GetMapping("/celdas/{id}/editar")
    public String editCell(@PathVariable long id, Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        model.addAttribute("celda", adminService.cell(id));
        return "celda-form";
    }

    @PostMapping("/celdas/{id}/editar")
    public String updateCell(@PathVariable long id, @RequestParam String tipo,
                             Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        try { adminService.updateCell(id, tipo, auth.getName()); return "redirect:/celdas/" + id; }
        catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage()); model.addAttribute("celda", adminService.cell(id));
            return "celda-form";
        }
    }

    @PostMapping("/celdas/{id}/estado")
    public String cellState(@PathVariable long id, @RequestParam String nuevoEstado,
                            Authentication auth, Model model) {
        model.addAttribute("supervisor", isSupervisor(auth));
        try { adminService.changeCellState(id, nuevoEstado, auth.getName()); return "redirect:/celdas/" + id; }
        catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage()); model.addAttribute("celda", adminService.cell(id));
            model.addAttribute("historial", adminService.cellHistory(id));
            return "celda-detalle";
        }
    }
    private boolean isSupervisor(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPERVISOR".equals(a.getAuthority()));
    }
}
