package br.com.conectaPro.api.demand;

import br.com.conectaPro.dto.AcceptDemandDTO;
import br.com.conectaPro.dto.DemandResponseDTO;
import br.com.conectaPro.dto.ReassignRequestDTO;
import br.com.conectaPro.dto.StatusUpdateDTO;
import br.com.conectaPro.model.category.Category;
import br.com.conectaPro.model.category.CategoryService;
import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.demand.DemandService;
import br.com.conectaPro.model.demand.DemandStatus;
import br.com.conectaPro.model.user.AddressUser;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserService;
import br.com.conectaPro.security.CustomUserDetails;
import br.com.conectaPro.util.Authz;
import br.com.conectaPro.util.Util;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/demand")
@CrossOrigin
@Tag(name = "Demand", description = "Demandas/serviços abertos pelo user do tipo cliente")
public class DemandController {

  @Autowired private DemandService demandService;

  @Autowired private CategoryService categoryService;

  @Autowired private UserService userService;

  @Operation(summary = "Criar entidade demanda")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> save(
      @Valid @ModelAttribute DemandRequest request,
      @RequestParam(value = "imagens", required = false) List<MultipartFile> imagens,
      @AuthenticationPrincipal CustomUserDetails principal) {

    // o cliente da demanda é sempre o usuário logado (não dá para criar em nome de outro)
    Authz.requireSelf(request.getClientId(), principal);

    try {
      User client = userService.getById(request.getClientId());
      User professional = userService.getById(request.getProfessionalId());
      Category category = categoryService.getById(request.getCategoryId());
      AddressUser address = userService.getAddressById(request.getAddressId());

      Demand demandNew = request.build();
      demandNew.setCategoryId(category);
      demandNew.setAddressId(address);
      demandNew.setClientId(client);
      demandNew.setProfessionalId(professional);
      demandNew.setDemandStatus(DemandStatus.ABERTO);

      String demandCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      demandNew.setCode(demandCode);

      List<String> urls = new ArrayList<>();
      if (imagens != null && !imagens.isEmpty()) {
        for (MultipartFile imagem : imagens) {
          if (imagem != null && !imagem.isEmpty()) {
            String nomeArquivo = Util.fazerUploadImagem(imagem);
            if (nomeArquivo == null) {
              throw new RuntimeException("Erro ao salvar imagem: " + imagem.getOriginalFilename());
            }
            urls.add(nomeArquivo);
          }
        }
      }
      demandNew.setImgUrl(urls);

      Demand demand = demandService.save(demandNew);
      return new ResponseEntity<>(DemandResponseDTO.fromEntity(demand), HttpStatus.CREATED);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("Um dos IDs informados (Cliente, Profissional, Categoria ou Endereço) não existe.");
    }
  }

  @Operation(
      summary = "Lista as demandas do usuário logado",
      description = "Só as demandas em que o usuário é o cliente ou o profissional.")
  @GetMapping("/user")
  public List<DemandResponseDTO> getAll(@AuthenticationPrincipal CustomUserDetails principal) {
    return DemandResponseDTO.fromList(demandService.getAllForUser(principal.getUser().getId()));
  }

  @Operation(summary = "Lista uma demanda especifica pelo ID")
  @GetMapping("/user/{id}")
  public DemandResponseDTO getById(
      @PathVariable @NonNull Long id, @AuthenticationPrincipal CustomUserDetails principal) {
    return DemandResponseDTO.fromEntity(
        demandService.getByIdForUser(id, principal.getUser().getId()));
  }

  @Operation(summary = "Atualiza campos especificos da demanda")
  @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> update(
      @PathVariable Long id,
      @ModelAttribute DemandRequest request,
      @RequestParam(value = "imagens", required = false) List<MultipartFile> imagens,
      @AuthenticationPrincipal CustomUserDetails principal) {

    Long userId = principal.getUser().getId();
    Demand demand = request.build();

    // Novas fotos são SOMADAS às existentes (nada é apagado do R2 por aqui; para remover use
    // DELETE /{id}/images/{nome}). Valida dono/status/teto antes de enviar qualquer arquivo.
    List<String> novasUrls = new ArrayList<>();
    if (imagens != null) {
      long enviadas = imagens.stream().filter(i -> i != null && !i.isEmpty()).count();
      demandService.assertCanAddImages(id, userId, (int) enviadas);
      for (MultipartFile imagem : imagens) {
        if (imagem != null && !imagem.isEmpty()) {
          String nomeArquivo = Util.fazerUploadImagem(imagem);
          if (nomeArquivo == null) {
            throw new RuntimeException("Erro ao salvar imagem: " + imagem.getOriginalFilename());
          }
          novasUrls.add(nomeArquivo);
        }
      }
    } else {
      demandService.getByIdForOwner(id, userId); // 404 se não for o dono
    }

    if (request.getCategoryId() != null) {
      demand.setCategoryId(categoryService.getById(request.getCategoryId()));
    }
    if (request.getProfessionalId() != null) {
      demand.setProfessionalId(userService.getById(request.getProfessionalId()));
    }
    if (request.getAddressId() != null) {
      demand.setAddressId(userService.getAddressById(request.getAddressId()));
    }

    demandService.updateForOwner(id, userId, demand, novasUrls);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Permite atualizar o profissional após a demanda ser rejeitada")
  @PatchMapping("/{id}/reassign")
  public ResponseEntity<DemandResponseDTO> reassignProfessional(
      @PathVariable @NonNull Long id,
      @RequestBody ReassignRequestDTO request,
      @AuthenticationPrincipal CustomUserDetails principal) {

    try {
      demandService.getByIdForOwner(id, principal.getUser().getId()); // só o cliente da demanda
      Demand updatedDemand = demandService.reassign(id, request.professionalId());
      return ResponseEntity.ok(DemandResponseDTO.fromEntity(updatedDemand));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
    }
  }

  @Operation(summary = "Profissional aceita a demanda e define o valor final do serviço")
  @PatchMapping("/{id}/accept")
  public ResponseEntity<?> accept(
      @PathVariable Long id,
      @RequestBody AcceptDemandDTO request,
      @AuthenticationPrincipal CustomUserDetails principal) {

    try {
      demandService.getByIdForProfessional(id, principal.getUser().getId()); // só o profissional
      Demand updated = demandService.acceptWithValue(id, request.getFinalValue());
      return ResponseEntity.ok(DemandResponseDTO.fromEntity(updated));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  @Operation(summary = "Atualiza o status da demanda")
  @PatchMapping("/{id}/status")
  public ResponseEntity<DemandResponseDTO> updateStatus(
      @PathVariable Long id,
      @RequestBody StatusUpdateDTO request,
      @AuthenticationPrincipal CustomUserDetails principal) {

    // recusar e finalizar são ações do profissional da demanda
    demandService.getByIdForProfessional(id, principal.getUser().getId());
    Demand updated = demandService.updateStatus(id, request.getStatus());
    return ResponseEntity.ok(DemandResponseDTO.fromEntity(updated));
  }

  @Operation(summary = "Remove uma imagem especifica de uma demanda")
  @DeleteMapping("/{id}/images/{nomeArquivo}")
  public ResponseEntity<?> deleteImage(
      @PathVariable Long id,
      @PathVariable String nomeArquivo,
      @AuthenticationPrincipal CustomUserDetails principal) {

    try {
      Demand updated =
          demandService.removeImageForOwner(id, principal.getUser().getId(), nomeArquivo);
      return ResponseEntity.ok(DemandResponseDTO.fromEntity(updated));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  @Operation(summary = "Deleta uma demanda")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable @NonNull Long id, @AuthenticationPrincipal CustomUserDetails principal) {
    demandService.deleteForOwner(id, principal.getUser().getId());
    return ResponseEntity.ok().build();
  }
}
