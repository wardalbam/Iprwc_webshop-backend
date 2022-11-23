package com.example.Iprwc_backend.Controller;


import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.PathParam;

import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.hibernate.annotations.Any;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.Iprwc_backend.DAO.AddressRepo;
import com.example.Iprwc_backend.DAO.OrderLineRepo;
import com.example.Iprwc_backend.DAO.OrderRepo;
import com.example.Iprwc_backend.DAO.ProductRepo;
import com.example.Iprwc_backend.DAO.UserRepo;
import com.example.Iprwc_backend.DTO.OrderDTO;
import com.example.Iprwc_backend.DTO.OrderLineDTO;
import com.example.Iprwc_backend.Model.Address;
import com.example.Iprwc_backend.Model.OrderDetails;
import com.example.Iprwc_backend.Model.OrderLine;
import com.example.Iprwc_backend.Model.OrderStatus;
import com.example.Iprwc_backend.Model.Product;
import com.example.Iprwc_backend.Model.User;
import com.example.Iprwc_backend.Service.UserServiceImpl;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.util.JSONPObject;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/order")
class OrderController {

    @Autowired
    OrderRepo repository;

    @Autowired
    OrderLineRepo orderLineRepo;

    @Autowired
    ProductRepo productRepo;

    @Autowired
    AddressRepo addressRepo;

    @Autowired
    UserRepo userRepo;

    private final UserServiceImpl userService;
    

   

    // @GetMapping
    // public ResponseEntity<List<Bestelling>> getAll() {
    //     try {
    //         List<Bestelling> items = new ArrayList<Bestelling>();

    //         repository.findAll().forEach(items::add);

    //         if (items.isEmpty())
    //             return new ResponseEntity<>(HttpStatus.NO_CONTENT);

    //         return new ResponseEntity<>(items, HttpStatus.OK);
    //     } catch (Exception e) {
    //         return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    //     }
    // }

    // @GetMapping("{id}")
    // public ResponseEntity<Order> getById(@PathVariable("id") Long id) {
    //     Optional<Order> existingItemOptional = repository.findById(id);

    //     if (existingItemOptional.isPresent()) {
    //         return new ResponseEntity<>(existingItemOptional.get(), HttpStatus.OK);
    //     } else {
    //         return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    //     }
    // }

    @PostMapping
    public ResponseEntity<OrderDetails> create(@RequestBody OrderDTO orderDTO, HttpServletRequest request) {
        // return error if orderDTO.productLineList is empty
        if (orderDTO.getProductLineList().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        // sround with try catch
        try{
            // get user
            Principal principal = request.getUserPrincipal();
            User user = userService.getUser(principal.getName());
            // create new address 
            Address address = orderDTO.getAddress();
            address.setLand("Nederland");
            addressRepo.save(address);
            // check if any of orderDTO.address fields are empty
            if (address.addressLine.isEmpty() || address.city.isEmpty() || address.zipcode.isEmpty()) {
                // add custom error message to response header 
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            } 
            // create new order
            OrderDetails order = new OrderDetails(
                new ArrayList<>(),
                user.getUsername(),
                address,
                0,
                OrderStatus.RECIEVED
            );
            // create new orderlines
            List<OrderLine> productLines = new ArrayList<>();
            // convert orderlinesdto to list of orderlines
            for (OrderLineDTO orderLineDTO : orderDTO.getProductLineList()) {
                Product product = orderLineDTO.getProduct();
                Integer amount = orderLineDTO.getAmount();
                OrderLine orderLine = new OrderLine(
                    order.getId(),
                    product.getId(),
                    amount,
                    product.getName(),
                    product.getPrice(),
                    product.getImagePath()
                );
                orderLineRepo.save(orderLine);
                productLines.add(orderLine);
            }
            // add orderlines to order
            order.setOrederLines(productLines);
            System.out.println(orderDTO.getProductLineList());
            // calculate total price
            double totalPrice = getTotalPriceOrder(orderDTO.getProductLineList(), order.getId());
           
			order.setTotalPrice(totalPrice);
            // save order
            OrderDetails savedItem = repository.save(order);
            return new ResponseEntity<>(savedItem, HttpStatus.CREATED);
        }catch(Exception e){
            System.out.println(e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
    }

    public OrderController(OrderRepo repository, OrderLineRepo orderLineRepo, ProductRepo productRepo,
            UserServiceImpl userService) {
        this.repository = repository;
        this.orderLineRepo = orderLineRepo;
        this.productRepo = productRepo;
        this.userService = userService;
    }

    public double getTotalPriceOrder(List<OrderLineDTO> producList, Long OrderId){
        double totalPrice = 0;
        for (OrderLineDTO orderLineDTO : producList) {
            Product product = orderLineDTO.getProduct();
            Integer amount = orderLineDTO.getAmount();
            totalPrice += product.getPrice() * amount;
        }
        return totalPrice;
    }


    @DeleteMapping("{id}")
    public ResponseEntity<HttpStatus> delete(@PathVariable("id") Long id) {
        try {
            repository.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
        }
    }

    // just keep it simple return list of orders and in the front end make it like the product list
    @GetMapping("/all")
    public ResponseEntity<List<OrderDetails>> getUserOrders(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        
        User user = userService.getUser(principal.getName());
        // if  one of user roles has  name role_admin return all orders 
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_ADMIN")) || user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_MANAGER"))) {
            
            return new ResponseEntity<>(repository.findAll(), HttpStatus.OK);
        }
        List<OrderDetails> existingItemOptional = repository.findByUsername(user.getUsername());

        if (existingItemOptional != null) {
            return new ResponseEntity<>(existingItemOptional, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/all/{orderId}")
    public ResponseEntity<List<OrderLine>> GetAllOrderLines(@PathVariable("orderId") Long orderId, @RequestHeader (name="Authorization") String token) {
        String token_1 = token.substring("Bearer ".length());
        Algorithm algorithm = Algorithm.HMAC256("secret".getBytes());
        JWTVerifier verifier = JWT.require(algorithm).build();
        DecodedJWT decodedJWT = verifier.verify(token_1);
        String username = decodedJWT.getSubject();
        // Long userId = userService.getUser(username).getId();
        Collection<OrderDetails> order = repository.getOrderBYOrderIdAndUserId(username, orderId);
        // // if order belongs to the user 
        if( !order.isEmpty() ){
            List<OrderLine> linesList = orderLineRepo.findLinesByOrderId(orderId);
            return new ResponseEntity<>(linesList, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // set status of an order by its id, no user needed
    
    @PutMapping("status")
    public ResponseEntity<OrderDetails> updateOrderStatus(@RequestParam() String orderId, @RequestParam() String status) {
        // check if the orderStatus enum contains the String status
        System.out.println("tttttttt----------------------------");
        System.out.println(orderId  + status );
        try{
            OrderStatus  orderStatus = OrderStatus.valueOf(status);
            // orderid from string to Long
            Long id = Long.parseLong(orderId);
            Optional<OrderDetails> orderOptional = repository.findById(id);
            if (orderOptional.isPresent()) {
                OrderDetails order = orderOptional.get();
                order.setOrderStatus(orderStatus);
                return new ResponseEntity<>(repository.save(order), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        }catch(Exception e){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    // get all orders for a user by user id 
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderDetails>> getUserOrders(@PathVariable("userId") Long userId) {
        // if user not found by userid: return bad request  
        if(userRepo.findById(userId).isPresent()){
            User user = userRepo.getById(userId);
            List<OrderDetails> existingItemOptional = repository.findByUsername(user.getUsername());
            if (existingItemOptional != null) {
                return new ResponseEntity<>(existingItemOptional, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        }else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    


    // get order by id
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetails> getOrderById(@PathVariable("id") Long id) {
        Optional<OrderDetails> orderOptional = repository.findById(id);
        if (orderOptional.isPresent()) {
            return new ResponseEntity<>(orderOptional.get(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

}