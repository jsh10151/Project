package com.spacepick;

import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceService {
    public record Space(long id,String name,String district,String address,String kind,int capacity,int hourlyPrice,String imageUrl,String description,List<String> amenities,String rules,int openHour,int closeHour) {}
    public record Create(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=50) String district,
        @NotBlank @Size(max=200) String address,@NotBlank @Pattern(regexp="촬영|모임|전시|워크숍") String kind,
        @Min(1) @Max(100) int capacity,@Min(1000) @Max(1000000) int hourlyPrice,
        @NotBlank @Size(max=1000) @Pattern(regexp="https://.*") String imageUrl,
        @NotBlank @Size(max=5000) String description,@NotBlank @Size(max=1000) String amenities,@NotBlank @Size(max=3000) String rules) {}
    private final JdbcTemplate db;
    public SpaceService(JdbcTemplate db){this.db=db;}
    private org.springframework.jdbc.core.RowMapper<Space> mapper(){return (r,i)->new Space(r.getLong("id"),r.getString("name"),r.getString("district"),r.getString("address"),r.getString("kind"),r.getInt("capacity"),r.getInt("hourly_price"),r.getString("image_url"),r.getString("description"),Arrays.asList(r.getString("amenities").split(",")),r.getString("rules"),r.getInt("open_hour"),r.getInt("close_hour"));}
    public List<Space> list(String district,String kind,Integer guests,Integer maxPrice){
        String sql="SELECT * FROM spaces WHERE 1=1";List<Object> values=new ArrayList<>();
        if(district!=null&&!district.isBlank()){sql+=" AND district=?";values.add(district);}
        if(kind!=null&&!kind.isBlank()){sql+=" AND kind=?";values.add(kind);}
        if(guests!=null){if(guests<1||guests>100)throw new ApiException(400,"인원을 확인해 주세요.");sql+=" AND capacity>=?";values.add(guests);}
        if(maxPrice!=null){if(maxPrice<0)throw new ApiException(400,"가격을 확인해 주세요.");sql+=" AND hourly_price<=?";values.add(maxPrice);}
        return db.query(sql+" ORDER BY id",mapper(),values.toArray());
    }
    public Space get(long id){return db.query("SELECT * FROM spaces WHERE id=?",mapper(),id).stream().findFirst().orElseThrow(()->new ApiException(404,"공간을 찾을 수 없습니다."));}
    public Space lock(long id){return db.query("SELECT * FROM spaces WHERE id=? FOR UPDATE",mapper(),id).stream().findFirst().orElseThrow(()->new ApiException(404,"공간을 찾을 수 없습니다."));}
    @Transactional public Space create(Create r){
        db.update("INSERT INTO spaces(name,district,address,kind,capacity,hourly_price,image_url,description,amenities,rules) VALUES (?,?,?,?,?,?,?,?,?,?)",r.name(),r.district(),r.address(),r.kind(),r.capacity(),r.hourlyPrice(),r.imageUrl(),r.description(),r.amenities(),r.rules());
        return get(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
    }
}
