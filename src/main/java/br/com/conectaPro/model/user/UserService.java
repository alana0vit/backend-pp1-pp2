package br.com.conectaPro.model.user;

import br.com.conectaPro.api.user.UserRequest;
import br.com.conectaPro.dto.CoordinatesDTO;
import br.com.conectaPro.model.category.Category;
import br.com.conectaPro.model.category.CategoryRepository;
import br.com.conectaPro.util.AgeUtils;
import br.com.conectaPro.util.GeoLocationService;
import br.com.conectaPro.util.Util;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final AddressUserRepository addressUserRepository;
  private final CategoryRepository categoryRepository;
  private final GeoLocationService geoLocationService;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository repository,
      AddressUserRepository addressUserRepository,
      CategoryRepository categoryRepository,
      GeoLocationService geoLocationService,
      PasswordEncoder passwordEncoder) {

    this.userRepository = repository;
    this.addressUserRepository = addressUserRepository;
    this.categoryRepository = categoryRepository;
    this.geoLocationService = geoLocationService;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public User save(UserRequest userRequest) {
    validateAge(userRequest.getBirthDate());
    validateProfessionalCategories(userRequest.getUserType(), userRequest.getCategoriesIds());

    if (userRepository.findByEmail(userRequest.getEmail()).isPresent()) {
      throw new IllegalStateException("E-mail já cadastrado.");
    }

    User user = userRequest.build();

    user.setPassword(passwordEncoder.encode(userRequest.getPassword()));

    if (userRequest.getCategoriesIds() != null && !userRequest.getCategoriesIds().isEmpty()) {
      List<Category> categories = categoryRepository.findAllById(userRequest.getCategoriesIds());
      user.setCategories(categories);
    }

    user.setEnabled(Boolean.TRUE);
    User savedUser = userRepository.save(user);

    // Instancia e salva o Address vinculado ao User
    AddressUser addressUser = userRequest.getAddress().build();
    try {
      CoordinatesDTO coords = geoLocationService.getCoordinates(addressUser);
      addressUser.setLatitude(Double.parseDouble(coords.getLat()));
      addressUser.setLongitude(Double.parseDouble(coords.getLon()));
      System.out.println("LAT: " + addressUser.getLatitude());
      System.out.println("LNG: " + addressUser.getLongitude());
    } catch (Exception e) {
      System.out.println("ERRO GEOLOCALIZACAO: " + e.getMessage());
    }

    addressUser.setUserId(savedUser);
    addressUser.setEnabled(Boolean.TRUE);
    addressUserRepository.save(addressUser);

    List<AddressUser> addresses = new ArrayList<>();
    addresses.add(addressUser);
    savedUser.setAdresses(addresses);

    return savedUser;
  }

  private void validateProfessionalCategories(UserType userType, List<Long> categoryIds) {
    if (UserType.CLIENT.equals(userType) && categoryIds != null && !categoryIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Erro de negócio: Clientes não podem possuir categorias profissionais vinculadas.");
    }
  }

  public User findByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () -> new NoSuchElementException("Usuário não encontrado com o email: " + email));
  }

  public List<User> getAll() {
    return userRepository.findAll();
  }

  /** Cadastro e edição só aceitam quem tem 18 anos ou mais (data futura/hoje também é recusada). */
  private void validateAge(LocalDate birthDate) {
    if (birthDate != null && !AgeUtils.isMaiorDeIdade(birthDate, LocalDate.now())) {
      throw new IllegalArgumentException(
          "É necessário ter 18 anos ou mais para usar a plataforma.");
    }
  }

  public User getById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado com o ID: " + id));
  }

  @Transactional
  public void update(Long id, UserRequest userRequest) {

    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () -> new NoSuchElementException("Usuário não encontrado com o ID: " + id));

    validateAge(userRequest.getBirthDate());
    validateProfessionalCategories(userRequest.getUserType(), userRequest.getCategoriesIds());

    user.setName(userRequest.getName());
    user.setEnterprise(userRequest.getEnterprise());
    user.setEmail(userRequest.getEmail());
    user.setBirthDate(userRequest.getBirthDate());
    user.setPhone(userRequest.getPhone());
    user.setUserType(userRequest.getUserType());
    user.setRegistryId(userRequest.getRegistryId());
    user.setPhoto(userRequest.getPhoto());

    if (userRequest.getPassword() != null && !userRequest.getPassword().isBlank()) {
      user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
    }

    if (userRequest.getCategoriesIds() != null) {
      List<Category> categories = categoryRepository.findAllById(userRequest.getCategoriesIds());
      user.setCategories(categories);
    } else {
      user.setCategories(new ArrayList<>());
    }

    userRepository.save(user);
  }

  @Transactional
  public void delete(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () -> new NoSuchElementException("Usuário não encontrado com o ID: " + id));
    user.setEnabled(Boolean.FALSE);
    userRepository.save(user);
  }

  @Transactional
  public User updatePhoto(Long id, MultipartFile foto) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () -> new NoSuchElementException("Usuário não encontrado com o ID: " + id));

    String nomeArquivo = Util.fazerUploadImagem(foto);
    if (nomeArquivo == null) {
      throw new RuntimeException("Erro ao salvar a foto: " + foto.getOriginalFilename());
    }

    String fotoAntiga = user.getPhoto();
    user.setPhoto(nomeArquivo);
    User saved = userRepository.save(user);

    // Só apaga a foto antiga depois de garantir que a nova já foi salva
    if (fotoAntiga != null && !fotoAntiga.isBlank()) {
      Util.apagarImagem(fotoAntiga);
    }

    return saved;
  }

  @Transactional
  public User deletePhoto(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () -> new NoSuchElementException("Usuário não encontrado com o ID: " + id));

    if (user.getPhoto() != null && !user.getPhoto().isBlank()) {
      Util.apagarImagem(user.getPhoto());
    }

    user.setPhoto(null);
    return userRepository.save(user);
  }

  public List<User> search(
      String name, Long categoryId, Double latitude, Double longitude, Double radiusKm) {

    if (radiusKm != null && radiusKm <= 0) {
      throw new IllegalArgumentException("O raio deve ser maior que zero");
    }

    return userRepository.searchUsers(name, categoryId, latitude, longitude, radiusKm);
  }

  /**
   * Busca para o mapa: igual à busca normal, mas rejeita combinações de parâmetros que fariam o
   * filtro por raio ser ignorado em silêncio (ex.: radiusKm sem latitude/longitude).
   */
  public List<User> searchForMap(
      String name, Long categoryId, Double latitude, Double longitude, Double radiusKm) {

    if ((latitude == null) != (longitude == null)) {
      throw new IllegalArgumentException("Informe latitude e longitude juntas.");
    }
    if (radiusKm != null && latitude == null) {
      throw new IllegalArgumentException("radiusKm exige latitude e longitude.");
    }
    if (latitude != null && (latitude < -90 || latitude > 90)) {
      throw new IllegalArgumentException("latitude deve estar entre -90 e 90.");
    }
    if (longitude != null && (longitude < -180 || longitude > 180)) {
      throw new IllegalArgumentException("longitude deve estar entre -180 e 180.");
    }

    return search(name, categoryId, latitude, longitude, radiusKm);
  }

  // Endereços

  public List<AddressUser> getAllAddressByUser(Long userId) {
    User user = this.getById(userId);
    return user.getAdresses();
  }

  public AddressUser getAddressById(Long id) {
    return addressUserRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Endereço não encontrado"));
  }

  @Transactional
  public AddressUser postAddressUser(Long userId, AddressUser address) {
    User user = this.getById(userId);

    try {
      CoordinatesDTO coords = geoLocationService.getCoordinates(address);
      address.setLatitude(Double.parseDouble(coords.getLat()));
      address.setLongitude(Double.parseDouble(coords.getLon()));
    } catch (Exception e) {
      System.out.println("ERRO GEOLOCALIZACAO: " + e.getMessage());
    }

    address.setUserId(user);
    address.setEnabled(Boolean.TRUE);
    addressUserRepository.save(address);

    List<AddressUser> listAddressUser = user.getAdresses();
    if (listAddressUser == null) {
      listAddressUser = new ArrayList<>();
    }
    listAddressUser.add(address);
    user.setAdresses(listAddressUser);
    userRepository.save(user);

    return address;
  }

  @Transactional
  public AddressUser updateAddressUser(Long id, AddressUser addressChanged) {
    AddressUser address =
        addressUserRepository
            .findById(id)
            .orElseThrow(
                () -> new NoSuchElementException("Endereço não encontrado com o ID: " + id));

    address.setStreet(addressChanged.getStreet());
    address.setNumber(addressChanged.getNumber());
    address.setNeighborhood(addressChanged.getNeighborhood());
    address.setCity(addressChanged.getCity());
    address.setState(addressChanged.getState());
    address.setZipCode(addressChanged.getZipCode());
    address.setSupplement(addressChanged.getSupplement());

    // Igual ao cadastro: se o geocoding falhar, não derruba a edição. As coordenadas antigas não
    // valem mais para o endereço novo, então são limpas (o endereço fica fora da busca por raio).
    try {
      CoordinatesDTO coords = geoLocationService.getCoordinates(address);
      address.setLatitude(Double.valueOf(coords.getLat()));
      address.setLongitude(Double.valueOf(coords.getLon()));
    } catch (Exception e) {
      System.out.println("ERRO GEOLOCALIZACAO: " + e.getMessage());
      address.setLatitude(null);
      address.setLongitude(null);
    }

    return addressUserRepository.save(address);
  }

  @Transactional
  public void deleteAddressUser(Long idAddress) {
    AddressUser address =
        addressUserRepository
            .findById(idAddress)
            .orElseThrow(
                () -> new NoSuchElementException("Endereço não encontrado com o ID: " + idAddress));

    address.setEnabled(Boolean.FALSE);
    addressUserRepository.save(address);

    User user = this.getById(address.getUserId().getId());
    user.getAdresses().remove(address);
    userRepository.save(user);
  }
}
