package br.com.conectaPro.api.user;

import br.com.conectaPro.dto.ProfessionalMapDTO;
import br.com.conectaPro.dto.PublicUserDTO;
import br.com.conectaPro.dto.UserResponseDTO;
import br.com.conectaPro.model.user.AddressUser;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserService;
import br.com.conectaPro.model.user.UserType;
import br.com.conectaPro.security.CustomUserDetails;
import br.com.conectaPro.util.Authz;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
@Tag(name = "User/Address")
public class UserController {
  @Autowired private UserService userService;

  @Operation(summary = "Criar entidade usuário")
  @PostMapping
  public ResponseEntity<User> save(@RequestBody @Valid UserRequest request) {
    User user = userService.save(request);
    return new ResponseEntity<>(user, HttpStatus.CREATED);
  }

  @Operation(
      summary = "Lista os profissionais (dados públicos)",
      description = "Só profissionais e só dados públicos (sem e-mail, telefone, CPF/CNPJ).")
  @GetMapping
  public ResponseEntity<List<PublicUserDTO>> getAll() {
    List<PublicUserDTO> response =
        userService.getAll().stream()
            .filter(u -> u.getUserType() == UserType.PROFESSIONAL)
            .map(PublicUserDTO::fromEntity)
            .toList();
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Busca um usuario pelo ID",
      description =
          "Se o ID for do próprio usuário logado, devolve os dados completos (UserResponseDTO)."
              + " Para qualquer outro usuário, devolve só o perfil público (PublicUserDTO).")
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(
      @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
    User user = userService.getById(id);

    if (id.equals(principal.getUser().getId())) {
      return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }
    return ResponseEntity.ok(PublicUserDTO.fromEntity(user));
  }

  @Operation(summary = "Atualiza um usuario pelo seu ID")
  @PutMapping("/{id}")
  public ResponseEntity<User> update(
      @PathVariable("id") Long id,
      @RequestBody UserRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(id, principal);

    userService.update(id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Deleta um usuario pelo seu ID")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(id, principal);

    userService.delete(id);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Envia/atualiza a foto de um usuario")
  @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UserResponseDTO> updatePhoto(
      @PathVariable Long id,
      @RequestParam("foto") MultipartFile foto,
      @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(id, principal);

    if (foto == null || foto.isEmpty()) {
      throw new IllegalArgumentException("Arquivo de imagem vazio ou não enviado (campo 'foto').");
    }

    User user = userService.updatePhoto(id, foto);
    return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
  }

  @Operation(summary = "Remove a foto de um usuario")
  @DeleteMapping("/{id}/photo")
  public ResponseEntity<UserResponseDTO> deletePhoto(
      @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(id, principal);
    User user = userService.deletePhoto(id);
    return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
  }

  @Operation(summary = "Filtro de busca por profissional (dados públicos)")
  @GetMapping("/search")
  public ResponseEntity<List<PublicUserDTO>> search(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam(required = false) Double radiusKm) {
    List<User> users = userService.search(name, categoryId, latitude, longitude, radiusKm);
    List<PublicUserDTO> response = users.stream().map(PublicUserDTO::fromEntity).toList();
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Profissionais para o mapa",
      description =
          "Versão enxuta da busca, sem dados pessoais: devolve só o necessário para desenhar pins"
              + " (coordenadas arredondadas) e a distância até a origem informada. Ordena por plano"
              + " (maior primeiro), depois por distância.")
  @GetMapping("/search/map")
  public ResponseEntity<List<ProfessionalMapDTO>> searchForMap(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam(required = false) Double radiusKm) {
    List<User> users = userService.searchForMap(name, categoryId, latitude, longitude, radiusKm);
    return ResponseEntity.ok(ProfessionalMapDTO.fromUsers(users, latitude, longitude));
  }

  // Endereços

  /** Endereço só para o dono (404 para os demais). */
  private AddressUser ownedAddress(Long addressId, CustomUserDetails principal) {
    AddressUser address = userService.getAddressById(addressId);
    Authz.requireSelf(address.getUserId() == null ? null : address.getUserId().getId(), principal);
    return address;
  }

  @Operation(summary = "Lista todos os endereços de um usuario")
  @GetMapping("/{userId}/addresses")
  public List<AddressUser> getAllAddresses(
      @PathVariable Long userId, @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(userId, principal);
    return userService.getAllAddressByUser(userId);
  }

  @Operation(summary = "Lista um endereço especifico")
  @GetMapping("/addresses/{addressId}")
  public AddressUser getAddressById(
      @PathVariable Long addressId, @AuthenticationPrincipal CustomUserDetails principal) {
    return ownedAddress(addressId, principal);
  }

  @Operation(summary = "Criar endereço para um usuario")
  @PostMapping("/{userId}/addresses")
  public ResponseEntity<AddressUser> postAddressUser(
      @PathVariable("userId") Long userId,
      @RequestBody @Valid AddressUserRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    Authz.requireSelf(userId, principal);

    AddressUser address = userService.postAddressUser(userId, request.build());
    return new ResponseEntity<>(address, HttpStatus.CREATED);
  }

  @Operation(summary = "Atualiza um endereço pelo seu ID")
  @PutMapping("/address/{addressId}")
  public ResponseEntity<AddressUser> updateAddressUser(
      @PathVariable("addressId") Long addressId,
      @RequestBody AddressUserRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    ownedAddress(addressId, principal);

    AddressUser address = userService.updateAddressUser(addressId, request.build());
    return new ResponseEntity<>(address, HttpStatus.OK);
  }

  @Operation(summary = "Deleta um endereço pelo seu ID")
  @DeleteMapping("/address/{addressId}")
  public ResponseEntity<Void> deleteAddressUser(
      @PathVariable("addressId") Long addressId,
      @AuthenticationPrincipal CustomUserDetails principal) {
    ownedAddress(addressId, principal);

    userService.deleteAddressUser(addressId);
    return ResponseEntity.noContent().build();
  }
}
