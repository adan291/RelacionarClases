package org.example.ejemplos;

import java.util.List;

public class Empresa {
    private String nombre;
    private List<Oficina> sucursales;
    private List<Empleado> empleados;

    public String getNombre() { return nombre; }
    public List<Oficina> getSucursales() { return sucursales; }
    public List<Empleado> getEmpleados() { return empleados; }
}
